import * as admin from "firebase-admin";
import * as ExcelJS from "exceljs";

export async function generateXlsx(uid: string, projectId: string, estimateId: string): Promise<string> {
  const db = admin.firestore();

  const [projectSnap, estimateSnap, profileSnap] = await Promise.all([
    db.collection("users").doc(uid).collection("projects").doc(projectId).get(),
    db.collection("users").doc(uid).collection("projects").doc(projectId).collection("estimates").doc(estimateId).get(),
    db.collection("users").doc(uid).collection("profile").doc("company").get()
  ]);

  const project = projectSnap.data() ?? { title: "Проєкт" };
  const estimate = estimateSnap.data();
  if (!estimate) throw new Error("Кошторис не знайдено");

  const lines: any[] = JSON.parse(estimate.linesJson ?? "[]");
  const markup: number = estimate.markupPercent ?? 0;

  const workbook = new ExcelJS.Workbook();
  workbook.creator = "КубМайстер";
  const sheet = workbook.addWorksheet("Кошторис");

  // Заголовок
  sheet.mergeCells("A1:F1");
  sheet.getCell("A1").value = `Кошторис: ${project.title}`;
  sheet.getCell("A1").font = { bold: true, size: 16, color: { argb: "FFCE1126" } };

  if (profileSnap.exists) {
    const co = profileSnap.data()!;
    sheet.mergeCells("A2:F2");
    sheet.getCell("A2").value = [co.name, co.address, co.phone].filter(Boolean).join(" | ");
    sheet.getCell("A2").font = { italic: true, color: { argb: "FF6C6C70" } };
  }

  // Шапка таблиці
  const headerRow = sheet.addRow(["#", "Найменування", "К-сть", "Од.", "Ціна, грн", "Сума, грн"]);
  headerRow.eachCell(cell => {
    cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFCE1126" } };
    cell.font = { bold: true, color: { argb: "FFFFFFFF" } };
    cell.alignment = { vertical: "middle", horizontal: "center" };
  });

  // Рядки
  let grandTotal = 0;
  lines.forEach((line, i) => {
    const lineTotal = line.applyMarkup && markup > 0
      ? line.qty * line.unitPrice * (1 + markup / 100)
      : line.qty * line.unitPrice;
    grandTotal += lineTotal;
    const row = sheet.addRow([i + 1, line.description, line.qty, line.unit, line.unitPrice, lineTotal]);
    if (i % 2 === 1) {
      row.eachCell(cell => {
        cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFF7F3EC" } };
      });
    }
    row.getCell(3).numFmt = "#,##0.00";
    row.getCell(5).numFmt = "#,##0.00";
    row.getCell(6).numFmt = "#,##0.00";
  });

  // Підсумок
  const totalRow = sheet.addRow(["", "Загальна сума", "", "", "", grandTotal]);
  totalRow.getCell(2).font = { bold: true };
  totalRow.getCell(6).font = { bold: true, color: { argb: "FFC9A227" } };
  totalRow.getCell(6).numFmt = "#,##0.00";
  totalRow.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFF7F3EC" } };

  // Ширина стовпців
  sheet.columns = [
    { width: 4 }, { width: 40 }, { width: 10 }, { width: 8 }, { width: 14 }, { width: 16 }
  ];

  if (markup > 0) {
    sheet.addRow([]);
    sheet.addRow([`* Включаючи націнку ${markup}% на відмічених позиціях`]);
  }

  const buffer = await workbook.xlsx.writeBuffer();
  const fileName = `estimates/${uid}/${projectId}/${estimateId}_${Date.now()}.xlsx`;
  const file = admin.storage().bucket().file(fileName);
  await file.save(Buffer.from(buffer as ArrayBuffer), {
    contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  });
  await file.makePublic();
  return file.publicUrl();
}
