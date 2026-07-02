import * as admin from "firebase-admin";
import * as cheerio from "cheerio";
import fetch from "node-fetch";

const RATE_LIMIT_MS = 2000; // затримка між запитами

interface PriceResult {
  sku: string;
  price?: number;
  vendor: string;
  url: string;
}

export async function refreshPrices(skus: string[]): Promise<{ updatedCount: number; skippedCount: number }> {
  const db = admin.firestore();
  const oneHourAgo = Date.now() - 60 * 60 * 1000;
  let updatedCount = 0;
  let skippedCount = 0;

  for (const sku of skus) {
    // Перевіряємо чи оновлювали за останню годину
    const existing = await db.collection("priceEntries")
      .where("materialSku", "==", sku)
      .where("source", "==", "Scraped")
      .orderBy("fetchedAt", "desc")
      .limit(1)
      .get();

    const lastFetch = existing.docs[0]?.data()?.fetchedAt ?? 0;
    if (lastFetch > oneHourAgo) {
      skippedCount++;
      continue;
    }

    await delay(RATE_LIMIT_MS);

    const result = await tryFetchEpicentr(sku);
    if (result?.price) {
      const entry = {
        materialSku: sku,
        vendor: result.vendor,
        unitPrice: result.price,
        currency: "UAH",
        source: "Scraped",
        fetchedAt: Date.now(),
        vendorUrl: result.url
      };
      await db.collection("priceEntries").add(entry);
      updatedCount++;
    } else {
      skippedCount++;
    }
  }

  return { updatedCount, skippedCount };
}

async function tryFetchEpicentr(sku: string): Promise<PriceResult | null> {
  const searchUrl = `https://epicentrk.ua/ua/search/?q=${encodeURIComponent(sku)}`;

  try {
    const res = await fetch(searchUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (compatible; CubeMaster price aggregator; rate-limited)",
        "Accept-Language": "uk-UA,uk;q=0.9"
      },
      signal: AbortSignal.timeout(10000)
    });

    if (!res.ok) return null;

    const html = await res.text();
    const $ = cheerio.load(html);

    // Шукаємо першу ціну у результатах пошуку
    const priceEl = $("[data-qaid='product_price'], .price__current").first();
    const priceText = priceEl.text().replace(/[^\d,\.]/g, "").replace(",", ".");
    const price = parseFloat(priceText);

    if (isNaN(price) || price <= 0) return null;

    const productLink = $("a[data-qaid='product_name'], .product__title a").first().attr("href");
    const url = productLink ? `https://epicentrk.ua${productLink}` : searchUrl;

    return { sku, price, vendor: "Епіцентр", url };
  } catch {
    return null;
  }
}

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
