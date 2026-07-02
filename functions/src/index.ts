import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { generatePdf } from "./generatePdf";
import { generateXlsx } from "./generateXlsx";
import { refreshPrices } from "./refreshPrices";

admin.initializeApp();

// ---- PDF генерація ----
export const generateEstimatePdf = functions
  .runWith({ memory: "1GB", timeoutSeconds: 120 })
  .https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Потрібна автентифікація");
    const { projectId, estimateId } = data;
    if (!projectId || !estimateId) {
      throw new functions.https.HttpsError("invalid-argument", "Відсутні projectId або estimateId");
    }
    const downloadUrl = await generatePdf(context.auth.uid, projectId, estimateId);
    return { downloadUrl };
  });

// ---- XLSX генерація ----
export const generateEstimateXlsx = functions
  .runWith({ memory: "512MB", timeoutSeconds: 60 })
  .https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Потрібна автентифікація");
    const { projectId, estimateId } = data;
    if (!projectId || !estimateId) {
      throw new functions.https.HttpsError("invalid-argument", "Відсутні projectId або estimateId");
    }
    const downloadUrl = await generateXlsx(context.auth.uid, projectId, estimateId);
    return { downloadUrl };
  });

// ---- Оновлення зовнішніх цін ----
export const refreshExternalPrices = functions
  .runWith({ memory: "256MB", timeoutSeconds: 120 })
  .https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Потрібна автентифікація");
    const { skus } = data as { skus: string[] };
    if (!skus?.length) throw new functions.https.HttpsError("invalid-argument", "Відсутній список SKU");
    const result = await refreshPrices(skus);
    return result;
  });

// ---- Scheduled оновлення цін щоночі ----
export const scheduledPriceRefresh = functions
  .pubsub.schedule("0 3 * * *")
  .timeZone("Europe/Kyiv")
  .onRun(async () => {
    const snapshot = await admin.firestore().collection("materialCatalog").get();
    const skus = snapshot.docs.map(d => d.id);
    if (skus.length > 0) {
      await refreshPrices(skus.slice(0, 50)); // обмеження за виклик
    }
    return null;
  });

// ---- Пошук каталогу ----
export const searchMaterialCatalog = functions.https.onCall(async (data) => {
  const { query, category } = data as { query: string; category?: string };
  let ref: FirebaseFirestore.Query = admin.firestore().collection("materialCatalog");
  if (category) ref = ref.where("category", "==", category);
  const snapshot = await ref.get();
  const results = snapshot.docs
    .map(d => ({ sku: d.id, ...d.data() }))
    .filter(item => !query || (item as any).nameUa?.toLowerCase().includes(query.toLowerCase()));
  return results.slice(0, 50);
});
