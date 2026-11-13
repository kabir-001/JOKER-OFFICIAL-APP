const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_vden";
const ADMIN_EMAIL = "vden4060@gmail.com";
const STUDENT_A_UID = "student_alice";
const STUDENT_B_UID = "student_bob";
const STUDENT_C_UID = "student_charlie";
const PUB_KEY_A = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAlicePublicKeyMockBase64Value12345";
const PUB_KEY_B = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBobPublicKeyMockBase64Value67890";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 2000);
}

function futureTs(hours = 24) {
  return new Date(Date.now() + hours * 3600 * 1000);
}

function validPaperPayload(uid, status = "published") {
  return {
    title: "Physics Class 10 Target Paper 2026",
    subject: "Physics",
    classLevel: "10th Class",
    examTerm: "Annual Board 2026",
    totalMarks: 75,
    durationText: "3 Hours",
    instructions: "Attempt all MCQs and any 5 short questions.",
    externalPdfUrl: "",
    sections: [
      {
        heading: "Section A: MCQs",
        marksLabel: "15 Marks",
        questions: [
          {
            number: 1,
            text: "The SI unit of electric charge is:",
            marks: 1,
            options: ["Ampere", "Coulomb", "Volt", "Ohm"],
            answer: "Coulomb",
          },
        ],
      },
    ],
    status,
    createdByUid: uid,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validSupportPayload(uid) {
  return {
    isPublic: true,
    whatsappChannelName: "JOKER OFFICIAL Channel",
    whatsappChannelUrl: "https://whatsapp.com/channel/0029VaZnOTr5vKA6nXxuWw3U",
    sscGroupName: "SSC (IX/X) Session",
    sscGroupUrl: "https://chat.whatsapp.com/C4IFHnqFt7dEojcnvGYHUK",
    hscGroupName: "HSC (XI/XII) Session",
    hscGroupUrl: "https://chat.whatsapp.com/KBZIZeWc2VXJoX8lY0bzCW",
    supportNote: "Reach out between 9 AM and 8 PM for target paper queries.",
    helpContacts: [
      {
        label: "Academic Help Desk",
        phoneNumber: "+923001234567",
        allowCall: true,
        allowWhatsApp: true,
      },
    ],
    updatedByUid: uid,
    updatedAt: nowTs(),
  };
}

function validUsernamePayload(username, uid, roleLabel = "student") {
  return {
    username,
    uid,
    publicKeyBase64: PUB_KEY_A,
    keyFingerprint: "A1B2-C3D4-E5F6",
    roleLabel,
    bio: "SSC 10th student",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };
}

function validRoomPayload(uid, roomType = "group", adminsOnlyPosting = false) {
  return {
    title: roomType === "channel" ? "Joker Official Broadcast" : "SSC Study Room",
    description: "End-to-end encrypted student room.",
    roomType,
    category: "SSC",
    isPublic: true,
    adminsOnlyPosting,
    memberCount: 0,
    roomKeyEnvelope: "wrapped_key_base64_1234567890",
    roomFingerprint: "1122-3344-5566",
    lastPostCiphertext: "",
    lastPostIv: "",
    lastPostAlias: "",
    createdByUid: uid,
    createdAt: nowTs(),
    updatedAt: nowTs(),
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

test("Unauthenticated user: cannot read or write target_papers or app_config", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("target_papers").where("status", "==", "published").get()
  );
  await assertFails(
    unauthDb.collection("app_config").doc("support_settings").get()
  );
  await assertFails(
    unauthDb.collection("target_papers").doc("paper_1").set(validPaperPayload("x"))
  );
});

test("Bootstrapped Admin: can create, update, list all, and delete target papers", async () => {
  const adminEmails = [
    ADMIN_EMAIL,
    "karachieduofficial@gmail.com",
    "kbir94720@gmail.com",
    "joker94720@gmail.com",
  ];
  for (const [idx, email] of adminEmails.entries()) {
    const uid = `admin_user_${idx}`;
    const adminDb = testEnv
      .authenticatedContext(uid, { email, email_verified: true })
      .firestore();

    const ref = adminDb.collection("target_papers").doc(`paper_${idx}`);
    await assertSucceeds(ref.set(validPaperPayload(uid, "draft")));
    await assertSucceeds(adminDb.collection("target_papers").get());
    await assertSucceeds(
      ref.update({
        status: "published",
        updatedAt: nowTs(),
      })
    );
    await assertSucceeds(ref.delete());
  }
});

test("Email Spoofing Test: unverified admin email cannot create or list drafts", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();

  await assertFails(
    spoofDb.collection("target_papers").doc("spoof_paper").set(validPaperPayload("spoof_uid"))
  );
  await assertFails(spoofDb.collection("target_papers").get());
});

test("Student: can query published papers, cannot query drafts or unfiltered collection", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("target_papers").doc("pub_1").set(validPaperPayload(ADMIN_UID, "published"));
    await db.collection("target_papers").doc("draft_1").set(validPaperPayload(ADMIN_UID, "draft"));
  });

  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  await assertSucceeds(
    studentDb.collection("target_papers").where("status", "==", "published").get()
  );
  await assertSucceeds(studentDb.collection("target_papers").doc("pub_1").get());
  await assertFails(studentDb.collection("target_papers").doc("draft_1").get());
  await assertFails(studentDb.collection("target_papers").get());
});

test("Student: cannot create, update, or delete target papers", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("target_papers").doc("pub_1").set(validPaperPayload(ADMIN_UID, "published"));
  });

  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  await assertFails(
    studentDb.collection("target_papers").doc("student_paper").set(validPaperPayload(STUDENT_A_UID))
  );
  await assertFails(
    studentDb.collection("target_papers").doc("pub_1").update({ title: "Hacked", updatedAt: nowTs() })
  );
  await assertFails(studentDb.collection("target_papers").doc("pub_1").delete());
});

test("Shadow Update & Poisoning Test: admin write with ghost field or oversized title fails", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const ghostPayload = {
    ...validPaperPayload(ADMIN_UID),
    ghostField: true,
  };
  await assertFails(adminDb.collection("target_papers").doc("paper_ghost").set(ghostPayload));

  const oversized = {
    ...validPaperPayload(ADMIN_UID),
    title: "A".repeat(200),
  };
  await assertFails(adminDb.collection("target_papers").doc("paper_big").set(oversized));
});

test("Support config: admin can save, signed-in student can read when public or missing, student cannot write", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  await assertSucceeds(studentDb.collection("app_config").doc("support_settings").get());

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    adminDb.collection("app_config").doc("support_settings").set(validSupportPayload(ADMIN_UID))
  );
  await assertSucceeds(studentDb.collection("app_config").doc("support_settings").get());
  await assertFails(
    studentDb.collection("app_config").doc("support_settings").update({ whatsappChannelUrl: "https://evil.test", updatedAt: nowTs() })
  );
});

test("User profiles: isolated per userId", async () => {
  const aliceDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(STUDENT_A_UID).set({
      userId: STUDENT_A_UID,
      displayName: "Alice",
      preferredClass: "10th Class",
      username: "alice_10",
      lowDataMode: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(bobDb.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(bobDb.collection("users").doc(STUDENT_A_UID).update({ displayName: "Bob", updatedAt: nowTs() }));
});

test("Usernames: exact lookup allowed, student directory scraping blocked, admin handle queryable, role spoofing blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_99").set(validUsernamePayload("alice_99", STUDENT_A_UID, "student"))
  );
  await assertFails(
    aliceDb.collection("usernames").doc("fake_admin").set(validUsernamePayload("fake_admin", STUDENT_A_UID, "admin"))
  );
  await assertSucceeds(
    adminDb.collection("usernames").doc("joker_official").set(validUsernamePayload("joker_official", ADMIN_UID, "admin"))
  );

  await assertSucceeds(bobDb.collection("usernames").doc("alice_99").get());
  await assertSucceeds(bobDb.collection("usernames").where("roleLabel", "==", "admin").get());
  await assertFails(bobDb.collection("usernames").get());
});

test("E2EE DM threads & messages: isolated strictly to participants", async () => {
  const threadId = `${STUDENT_A_UID}_${STUDENT_B_UID}`;
  const aliceDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(STUDENT_C_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("dm_threads").doc(threadId).set({
      participantUids: [STUDENT_A_UID, STUDENT_B_UID],
      participantUsernames: ["alice_99", "bob_11"],
      participantPublicKeys: [PUB_KEY_A, PUB_KEY_B],
      lastMessageCiphertext: "c2VjcmV0X2NpcGhlcg==",
      lastMessageIv: "aXYxMjM0NTY=",
      lastSenderUsername: "alice_99",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("dm_threads").doc(threadId).collection("messages").doc("msg_1").set({
      threadId,
      participantUids: [STUDENT_A_UID, STUDENT_B_UID],
      senderUid: STUDENT_A_UID,
      senderUsername: "alice_99",
      senderPublicKey: PUB_KEY_A,
      recipientPublicKey: PUB_KEY_B,
      ciphertextBase64: "ZW5jcnlwdGVkUGF5bG9hZA==",
      ivBase64: "cmFuZG9tSXYxMg==",
      payloadType: "text",
      approxBytes: 120,
      createdAt: nowTs(),
    })
  );

  await assertSucceeds(
    bobDb.collection("dm_threads").doc(threadId).collection("messages").where("participantUids", "array-contains", STUDENT_B_UID).get()
  );
  await assertFails(charlieDb.collection("dm_threads").doc(threadId).get());
  await assertFails(
    charlieDb.collection("dm_threads").doc(threadId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("dm_threads").doc(threadId).collection("messages").where("participantUids", "array-contains", STUDENT_A_UID).get()
  );
});

test("Anonymous Community Groups & Channels: hidden membership, atomic follower count, channel posting restricted", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();

  await assertSucceeds(
    adminDb.collection("community_rooms").doc("grp_ssc").set(validRoomPayload(ADMIN_UID, "group", false))
  );
  await assertSucceeds(
    adminDb.collection("community_rooms").doc("ch_joker").set(validRoomPayload(ADMIN_UID, "channel", true))
  );
  await assertFails(
    aliceDb.collection("community_rooms").doc("rogue_room").set(validRoomPayload(STUDENT_A_UID, "group", false))
  );

  // Unpaired fake count update must fail
  await assertFails(
    aliceDb.collection("community_rooms").doc("grp_ssc").update({ memberCount: 100, updatedAt: nowTs() })
  );

  // Atomic batch join (+1) succeeds
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("community_rooms").doc("grp_ssc").collection("memberships").doc(STUDENT_A_UID), {
    userId: STUDENT_A_UID,
    joinedAt: nowTs(),
  });
  batch.update(aliceDb.collection("community_rooms").doc("grp_ssc"), {
    memberCount: 1,
    updatedAt: nowTs(),
  });
  await assertSucceeds(batch.commit());

  // Membership listing is strictly forbidden so identities never leak!
  await assertFails(aliceDb.collection("community_rooms").doc("grp_ssc").collection("memberships").get());
  await assertFails(bobDb.collection("community_rooms").doc("grp_ssc").collection("memberships").doc(STUDENT_A_UID).get());
  await assertFails(adminDb.collection("community_rooms").doc("grp_ssc").collection("memberships").get());

  // Joined student can post in unlocked group with isAdminPost == false
  await assertSucceeds(
    aliceDb.collection("community_rooms").doc("grp_ssc").collection("messages").doc("m1").set({
      roomId: "grp_ssc",
      isPublicRoom: true,
      senderUid: STUDENT_A_UID,
      senderAlias: "Student #4F2A",
      isAdminPost: false,
      ciphertextBase64: "ZW5jcnlwdGVkR3JvdXBNc2c=",
      ivBase64: "aXYxMjM0NTY=",
      payloadType: "text",
      approxBytes: 90,
      createdAt: nowTs(),
    })
  );

  // Student cannot spoof admin post or post in Admin-Only channel
  await assertFails(
    aliceDb.collection("community_rooms").doc("grp_ssc").collection("messages").doc("m_spoof").set({
      roomId: "grp_ssc",
      isPublicRoom: true,
      senderUid: STUDENT_A_UID,
      senderAlias: "Joker Official",
      isAdminPost: true,
      ciphertextBase64: "ZW5jcnlwdGVk",
      ivBase64: "aXYxMjM=",
      payloadType: "text",
      approxBytes: 50,
      createdAt: nowTs(),
    })
  );
  await assertFails(
    aliceDb.collection("community_rooms").doc("ch_joker").collection("messages").doc("c1").set({
      roomId: "ch_joker",
      isPublicRoom: true,
      senderUid: STUDENT_A_UID,
      senderAlias: "Student #4F2A",
      isAdminPost: false,
      ciphertextBase64: "ZW5jcnlwdGVk",
      ivBase64: "aXYxMjM=",
      payloadType: "text",
      approxBytes: 50,
      createdAt: nowTs(),
    })
  );
  await assertSucceeds(
    adminDb.collection("community_rooms").doc("ch_joker").collection("messages").doc("c_admin").set({
      roomId: "ch_joker",
      isPublicRoom: true,
      senderUid: ADMIN_UID,
      senderAlias: "Joker Official",
      isAdminPost: true,
      ciphertextBase64: "ZW5jcnlwdGVkQWRtaW4=",
      ivBase64: "aXYxMjM=",
      payloadType: "text",
      approxBytes: 75,
      createdAt: nowTs(),
    })
  );
});

test("E2EE Status Updates: private contact scoping and official broadcast protection", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(STUDENT_C_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("status_updates").doc("st_priv").set({
      authorUid: STUDENT_A_UID,
      authorLabel: "@alice_99",
      isOfficialBroadcast: false,
      isPublic: false,
      allowedUids: [STUDENT_A_UID, STUDENT_B_UID],
      wrappedKeys: [`${STUDENT_B_UID}:iv:key`],
      authorPublicKey: PUB_KEY_A,
      ciphertextBase64: "ZW5jcnlwdGVkU3RhdHVz",
      ivBase64: "aXYxMjM0",
      accentHex: "#064E3B",
      hasImage: false,
      approxBytes: 88,
      viewCount: 0,
      expiresAt: futureTs(24),
      createdAt: nowTs(),
    })
  );

  // Student cannot mark status as official broadcast
  await assertFails(
    aliceDb.collection("status_updates").doc("st_spoof").set({
      authorUid: STUDENT_A_UID,
      authorLabel: "Joker Official",
      isOfficialBroadcast: true,
      isPublic: true,
      allowedUids: [STUDENT_A_UID],
      wrappedKeys: [],
      authorPublicKey: PUB_KEY_A,
      ciphertextBase64: "ZW5jcnlwdGVk",
      ivBase64: "aXYxMjM=",
      accentHex: "#064E3B",
      hasImage: false,
      approxBytes: 60,
      viewCount: 0,
      expiresAt: futureTs(24),
      createdAt: nowTs(),
    })
  );

  await assertSucceeds(
    adminDb.collection("status_updates").doc("st_official").set({
      authorUid: ADMIN_UID,
      authorLabel: "Joker Official",
      isOfficialBroadcast: true,
      isPublic: true,
      allowedUids: [ADMIN_UID],
      wrappedKeys: [],
      authorPublicKey: PUB_KEY_A,
      ciphertextBase64: "b2ZmaWNpYWxTdGF0dXM=",
      ivBase64: "aXYxMjM=",
      accentHex: "#3F2E0A",
      hasImage: false,
      approxBytes: 96,
      viewCount: 0,
      expiresAt: futureTs(24),
      createdAt: nowTs(),
    })
  );

  await assertSucceeds(bobDb.collection("status_updates").doc("st_priv").get());
  await assertFails(charlieDb.collection("status_updates").doc("st_priv").get());
  await assertSucceeds(charlieDb.collection("status_updates").where("isPublic", "==", true).get());
  await assertSucceeds(bobDb.collection("status_updates").where("allowedUids", "array-contains", STUDENT_B_UID).get());
});
