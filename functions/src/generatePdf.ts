import * as admin from "firebase-admin";
import * as Handlebars from "handlebars";
import chromium from "@sparticuz/chromium";
import puppeteer from "puppeteer-core";

const pdfTemplate = `<!DOCTYPE html>
<html lang="uk">
<head>
<meta charset="UTF-8">
<style>
  body { font-family: Arial, sans-serif; margin: 32px; color: #1C1C1E; }
  h1 { color: #CE1126; font-size: 22px; margin-bottom: 4px; }
  .subtitle { color: #6C6C70; font-size: 13px; margin-bottom: 24px; }
  .company { border-left: 3px solid #CE1126; padding-left: 12px; margin-bottom: 24px; }
  table { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 13px; }
  th { background: #CE1126; color: white; padding: 8px 10px; text-align: left; }
  td { padding: 7px 10px; border-bottom: 1px solid #E5E0D8; }
  tr:nth-child(even) td { background: #F7F3EC; }
  .total-row td { font-weight: bold; background: #F7F3EC; color: #C9A227; font-size: 15px; }
  .ornament { border-top: 2px solid #CE11261A; margin: 16px 0; }
  .footer { margin-top: 32px; font-size: 11px; color: #6C6C70; text-align: center; }
</style>
</head>
<body>
<h1>Кошторис</h1>
<div class="subtitle">{{project.title}}{{#if project.address}} — {{project.address}}{{/if}}</div>
<div class="ornament"></div>
{{#if company.name}}
<div class="company">
  <strong>{{company.name}}</strong><br>
  {{#if company.address}}{{company.address}}<br>{{/if}}
  {{#if company.phone}}{{company.phone}}<br>{{/if}}
  {{#if company.email}}{{company.email}}{{/if}}
</div>
{{/if}}
<table>
  <thead>
    <tr>
      <th>#</th>
      <th>Найменування</th>
      <th>К-сть</th>
      <th>Од.</th>
      <th>Ціна, грн</th>
      <th>Сума, грн</th>
    </tr>
  </thead>
  <tbody>
    {{#each lines}}
    <tr>
      <td>{{inc @index}}</td>
      <td>{{description}}</td>
      <td>{{formatNum qty}}</td>
      <td>{{unit}}</td>
      <td>{{formatNum unitPrice}}</td>
      <td>{{formatNum total}}</td>
    </tr>
    {{/each}}
    <tr class="total-row">
      <td colspan="5">Загальна сума</td>
      <td>{{formatNum grandTotal}} грн</td>
    </tr>
  </tbody>
</table>
{{#if estimate.markupPercent}}
<div class="subtitle">* Включаючи націнку {{estimate.markupPercent}}% на позиціях з позначкою</div>
{{/if}}
<div class="footer">Дата: {{date}}</div>
</body>
</html>`;

Handlebars.registerHelper("inc", (v: number) => v + 1);
Handlebars.registerHelper("formatNum", (v: number) =>
  new Intl.NumberFormat("uk-UA", { minimumFractionDigits: 2 }).format(v ?? 0)
);

export async function generatePdf(uid: string, projectId: string, estimateId: string): Promise<string> {
  const db = admin.firestore();

  const [projectSnap, estimateSnap, profileSnap] = await Promise.all([
    db.collection("users").doc(uid).collection("projects").doc(projectId).get(),
    db.collection("users").doc(uid).collection("projects").doc(projectId).collection("estimates").doc(estimateId).get(),
    db.collection("users").doc(uid).collection("profile").doc("company").get()
  ]);

  const project = projectSnap.data() ?? { title: "Проєкт" };
  const estimate = estimateSnap.data();
  if (!estimate) throw new Error("Кошторис не знайдено");

  const lines: any[] = JSON.parse(estimate.linesJson ?? "[]").map((l: any) => ({
    ...l,
    total: l.applyMarkup && estimate.markupPercent > 0
      ? l.qty * l.unitPrice * (1 + estimate.markupPercent / 100)
      : l.qty * l.unitPrice
  }));

  const grandTotal = lines.reduce((sum, l) => sum + l.total, 0);
  const company = profileSnap.exists ? profileSnap.data() : {};

  const template = Handlebars.compile(pdfTemplate);
  const html = template({
    project,
    estimate: { ...estimate },
    lines,
    grandTotal,
    company,
    date: new Intl.DateTimeFormat("uk-UA").format(new Date())
  });

  const browser = await puppeteer.launch({
    args: chromium.args,
    executablePath: await chromium.executablePath(),
    headless: true
  });

  try {
    const page = await browser.newPage();
    await page.setContent(html, { waitUntil: "domcontentloaded" });
    const pdfBuffer = await page.pdf({ format: "A4", printBackground: true });
    const fileName = `estimates/${uid}/${projectId}/${estimateId}_${Date.now()}.pdf`;
    const file = admin.storage().bucket().file(fileName);
    await file.save(Buffer.from(pdfBuffer), { contentType: "application/pdf" });
    await file.makePublic();
    return file.publicUrl();
  } finally {
    await browser.close();
  }
}
