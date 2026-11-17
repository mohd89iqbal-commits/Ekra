const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function validOrderPayload(uid) {
  const ts = pastTimestamp(10000);
  return {
    userId: uid,
    restaurantId: "rest_firewood",
    restaurantName: "Napoli Ember Kitchen",
    restaurantCuisine: "Wood-Fired Italian",
    items: [
      {
        dishId: "dish_margherita",
        name: "Buffalo Margherita Pizza",
        quantity: 1,
        unitPriceCents: 1850,
      },
    ],
    deliveryAddressLabel: "Home",
    deliveryAddressLine: "420 Valencia St, Apt 4B, San Francisco",
    deliveryInstructions: "Leave at lobby desk",
    paymentMethodSummary: "Visa •••• 4242",
    paymentStatus: "PAID",
    transactionRef: "TXN-849201",
    promoCode: "LOCAL20",
    subtotalCents: 1850,
    deliveryFeeCents: 299,
    taxCents: 148,
    discountCents: 370,
    totalCents: 1927,
    status: "PLACED",
    etaMinutes: 26,
    courierName: "Marcus Vance",
    courierRating: 4.9,
    courierVehicle: "E-Bike • BL-402",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/orders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/addresses`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/payment_methods`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/favorites`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/orders`).doc("ord_1").set(validOrderPayload(ALICE_UID))
  );
});

test("Cross-user isolation blocks Bob from reading or writing Alice's subcollections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/orders`).doc("ord_1").set(validOrderPayload(ALICE_UID))
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/orders`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/orders`).doc("ord_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/orders`).doc("ord_1").update({
      status: "CONFIRMED",
      etaMinutes: 22,
      updatedAt: pastTimestamp(1000),
    })
  );
});

test("Address create, partial update, and delete follow rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTimestamp(10000);
  const addrRef = aliceDb.collection(`users/${ALICE_UID}/addresses`).doc("addr_home");

  await assertSucceeds(
    addrRef.set({
      userId: ALICE_UID,
      label: "Home",
      street: "420 Valencia St",
      apartment: "Apt 4B",
      city: "San Francisco",
      instructions: "Ring bell 4B",
      defaultAddress: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    addrRef.update({
      apartment: "Apt 4C",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/addresses`).doc("addr_ghost").set({
      id: "addr_ghost",
      userId: ALICE_UID,
      label: "Work",
      street: "100 Market St",
      city: "San Francisco",
      defaultAddress: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(addrRef.delete());
});

test("Tokenized payment method create, update, and future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTimestamp(10000);
  const pmRef = aliceDb.collection(`users/${ALICE_UID}/payment_methods`).doc("pm_visa_4242");

  await assertSucceeds(
    pmRef.set({
      userId: ALICE_UID,
      methodType: "CARD",
      brand: "Visa",
      last4OrHandle: "4242",
      holderName: "Alice Rivera",
      expiryMonth: 12,
      expiryYear: 2028,
      defaultMethod: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/payment_methods`).doc("pm_future").set({
      userId: ALICE_UID,
      methodType: "UPI",
      brand: "UPI",
      last4OrHandle: "alice@okaxis",
      holderName: "Alice Rivera",
      defaultMethod: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(
    pmRef.update({
      defaultMethod: false,
      updatedAt: pastTimestamp(1000),
    })
  );
});

test("Order lifecycle enforces valid transitions, terminal lock, and post-delivery rating", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const orderRef = aliceDb.collection(`users/${ALICE_UID}/orders`).doc("ord_live_1");

  // Cannot create directly as DELIVERED
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/orders`).doc("ord_bad_status").set({
      ...validOrderPayload(ALICE_UID),
      status: "DELIVERED",
    })
  );

  // Valid create in PLACED
  await assertSucceeds(orderRef.set(validOrderPayload(ALICE_UID)));

  // Cannot skip PLACED -> DELIVERED
  await assertFails(
    orderRef.update({
      status: "DELIVERED",
      etaMinutes: 0,
      updatedAt: pastTimestamp(4000),
    })
  );

  // Valid sequential progression
  await assertSucceeds(
    orderRef.update({ status: "CONFIRMED", etaMinutes: 22, updatedAt: pastTimestamp(4000) })
  );
  await assertSucceeds(
    orderRef.update({ status: "PREPARING", etaMinutes: 16, updatedAt: pastTimestamp(3000) })
  );
  await assertSucceeds(
    orderRef.update({ status: "OUT_FOR_DELIVERY", etaMinutes: 8, updatedAt: pastTimestamp(2000) })
  );
  await assertSucceeds(
    orderRef.update({ status: "DELIVERED", etaMinutes: 0, updatedAt: pastTimestamp(1000) })
  );

  // Terminal state lock: cannot cancel or change status after DELIVERED
  await assertFails(
    orderRef.update({
      status: "CANCELLED",
      paymentStatus: "REFUNDED",
      etaMinutes: 0,
      updatedAt: pastTimestamp(500),
    })
  );

  // Can submit userRating once after DELIVERED
  await assertSucceeds(
    orderRef.update({
      userRating: 5,
      updatedAt: pastTimestamp(400),
    })
  );

  // Cannot re-rate or delete order
  await assertFails(
    orderRef.update({
      userRating: 1,
      updatedAt: pastTimestamp(300),
    })
  );
  await assertFails(orderRef.delete());
});

test("Favorites collection create, list, and delete work for owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const favRef = aliceDb.collection(`users/${ALICE_UID}/favorites`).doc("rest_firewood");

  await assertSucceeds(
    favRef.set({
      userId: ALICE_UID,
      restaurantId: "rest_firewood",
      restaurantName: "Napoli Ember Kitchen",
      cuisine: "Wood-Fired Italian",
      rating: 4.8,
      createdAt: pastTimestamp(2000),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/favorites`).get());
  await assertSucceeds(favRef.delete());
});
