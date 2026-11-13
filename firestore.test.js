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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 2000);
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
  // Before creation, student get on non-existent doc is permitted safely
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
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(bobDb.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(bobDb.collection("users").doc(STUDENT_A_UID).update({ displayName: "Bob", updatedAt: nowTs() }));
});
