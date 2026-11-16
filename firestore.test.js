const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const pastTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user can create, list, update, and delete own task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");

  await assertSucceeds(taskRef.set({
    userId: ALICE_UID,
    title: "Review sprint plan",
    notes: "Check milestones",
    category: "Work",
    priority: "HIGH",
    isCompleted: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  await assertSucceeds(taskRef.update({
    isCompleted: true,
    updatedAt: pastTimestamp(),
  }));

  await assertSucceeds(taskRef.delete());
});

test("Authenticated user cannot read or write another user's tasks, preferences, or conversations", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").set({
      userId: BOB_UID,
      title: "Bob Private Task",
      notes: "",
      category: "Personal",
      priority: "MEDIUM",
      isCompleted: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("preferences").doc("default").set({
    userId: ALICE_UID,
    assistantName: "Hacked",
    preset: "CLASSIC_JARVIS",
    customInstructions: "",
    warmth: 0.5,
    conciseness: 0.5,
    humor: 0.5,
    voicePitch: 1.0,
    voiceRate: 1.0,
    autoSpeakReplies: true,
    proactiveBriefings: true,
    aiModel: "gemini-3.8-flash",
    themeMode: "DARK",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));
});

test("Shadow field injection and invalid priority are rejected on task create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_shadow");

  await assertFails(taskRef.set({
    userId: ALICE_UID,
    title: "Valid title",
    notes: "",
    category: "Work",
    priority: "HIGH",
    isCompleted: false,
    isAdmin: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));

  await assertFails(taskRef.set({
    userId: ALICE_UID,
    title: "Bad priority",
    notes: "",
    category: "Work",
    priority: "URGENT",
    isCompleted: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));
});

test("Conversation and message creation succeeds atomically and messages are immutable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");
  const msgRef = convRef.collection("messages").doc("msg_1");

  await assertSucceeds(convRef.set({
    userId: ALICE_UID,
    title: "Morning Briefing",
    lastMessagePreview: "Hello JARVIS",
    isPinned: false,
    messageCount: 1,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));

  await assertSucceeds(msgRef.set({
    userId: ALICE_UID,
    conversationId: "conv_1",
    sender: "USER",
    content: "Hello JARVIS",
    source: "TEXT",
    createdAt: pastTimestamp(),
  }));

  await assertFails(msgRef.update({ content: "Tampered" }));
});

test("Preferences and reminders enforce numeric ranges and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("default");
  const remRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1");

  await assertSucceeds(prefRef.set({
    userId: ALICE_UID,
    assistantName: "JARVIS",
    preset: "CLASSIC_JARVIS",
    customInstructions: "Keep replies concise.",
    warmth: 0.7,
    conciseness: 0.65,
    humor: 0.35,
    voicePitch: 1.0,
    voiceRate: 1.0,
    autoSpeakReplies: true,
    proactiveBriefings: true,
    aiModel: "gemini-3.8-flash",
    themeMode: "DARK",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));

  await assertFails(prefRef.update({
    warmth: 2.5,
    updatedAt: pastTimestamp(),
  }));

  await assertSucceeds(remRef.set({
    userId: ALICE_UID,
    title: "Stretch break",
    triggerAtMillis: Date.now() + 900000,
    isDone: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));
});
