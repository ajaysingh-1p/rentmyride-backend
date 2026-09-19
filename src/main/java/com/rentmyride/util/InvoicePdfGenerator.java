package com.rentmyride.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rentmyride.dtos.InvoiceDTO;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

// Builds an actual formatted PDF for a rental invoice (line items, GST split, totals) —
// replaces the earlier placeholder that just wrote plain text bytes with a .pdf extension.
public final class InvoicePdfGenerator {

    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(255, 107, 0));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(30, 30, 30));
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(120, 120, 120));
    private static final Font VALUE_FONT   = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 30, 30));
    private static final Font TABLE_HEAD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TOTAL_FONT   = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(255, 107, 0));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private InvoicePdfGenerator() {}

    public static byte[] generate(InvoiceDTO dto) throws Exception {
        Document document = new Document(PageSize.A4, 40, 40, 50, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        // ── Header ──
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{2, 1});

        PdfPCell brandCell = new PdfPCell();
        brandCell.setBorder(Rectangle.NO_BORDER);
        brandCell.addElement(new Paragraph("RentMyRide", TITLE_FONT));
        brandCell.addElement(new Paragraph("Dharm Dev Travels", LABEL_FONT));
        header.addCell(brandCell);

        PdfPCell invoiceMetaCell = new PdfPCell();
        invoiceMetaCell.setBorder(Rectangle.NO_BORDER);
        invoiceMetaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph invNo = new Paragraph("INVOICE", HEADING_FONT);
        invNo.setAlignment(Element.ALIGN_RIGHT);
        invoiceMetaCell.addElement(invNo);
        invoiceMetaCell.addElement(rightLine("# " + safe(dto.getInvoiceNumber())));
        if (dto.getInvoiceDate() != null) invoiceMetaCell.addElement(rightLine("Date: " + dto.getInvoiceDate().format(DATE_FMT)));
        if (dto.getDueDate() != null) invoiceMetaCell.addElement(rightLine("Due: " + dto.getDueDate().format(DATE_FMT)));
        header.addCell(invoiceMetaCell);

        document.add(header);
        document.add(spacer(20));

        // ── Bill To / Trip Info ──
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

        PdfPCell billTo = plainCell();
        billTo.addElement(new Paragraph("BILL TO", LABEL_FONT));
        billTo.addElement(new Paragraph(safe(dto.getCustomerName()), VALUE_FONT));
        billTo.addElement(new Paragraph(safe(dto.getCustomerMobile()), VALUE_FONT));
        billTo.addElement(new Paragraph(safe(dto.getCustomerEmail()), VALUE_FONT));
        if (dto.getCustomerAddress() != null && !dto.getCustomerAddress().isBlank())
            billTo.addElement(new Paragraph(dto.getCustomerAddress(), VALUE_FONT));
        infoTable.addCell(billTo);

        PdfPCell tripInfo = plainCell();
        tripInfo.addElement(new Paragraph("VEHICLE", LABEL_FONT));
        tripInfo.addElement(new Paragraph(safe(dto.getCarBrand()) + " " + safe(dto.getCarModel()), VALUE_FONT));
        tripInfo.addElement(new Paragraph(safe(dto.getCarRegistrationNumber()), VALUE_FONT));
        infoTable.addCell(tripInfo);

        document.add(infoTable);
        document.add(spacer(20));

        // ── Charges Table ──
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 1});

        addTableHeader(table, "Description", "Amount (₹)");

        if (dto.getBaseRentAmount() != null)
            addRow(table, "Base Rent" + (dto.getTotalDays() != null ? " (" + dto.getTotalDays() + " day(s)"
                    + (dto.getRentPerDay() != null ? " @ ₹" + fmt(dto.getRentPerDay()) + "/day)" : ")") : ""), dto.getBaseRentAmount());
        if (dto.getExtraKmCharges() != null && dto.getExtraKmCharges() > 0)
            addRow(table, "Extra KM Charges", dto.getExtraKmCharges());
        if (dto.getDamageCharges() != null && dto.getDamageCharges() > 0)
            addRow(table, "Damage Charges", dto.getDamageCharges());
        if (dto.getLateReturnCharges() != null && dto.getLateReturnCharges() > 0)
            addRow(table, "Late Return Charges", dto.getLateReturnCharges());
        if (dto.getDiscountAmount() != null && dto.getDiscountAmount() > 0)
            addRow(table, "Discount", -dto.getDiscountAmount());

        document.add(table);
        document.add(spacer(15));

        // ── Totals ──
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(50);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new float[]{2, 1});

        addTotalRow(totals, "Subtotal", dto.getSubtotal(), false);
        if (dto.getCgstAmount() != null)
            addTotalRow(totals, "CGST" + (dto.getCgstPercentage() != null ? " (" + fmt(dto.getCgstPercentage()) + "%)" : ""), dto.getCgstAmount(), false);
        if (dto.getSgstAmount() != null)
            addTotalRow(totals, "SGST" + (dto.getSgstPercentage() != null ? " (" + fmt(dto.getSgstPercentage()) + "%)" : ""), dto.getSgstAmount(), false);
        addTotalRow(totals, "Grand Total", dto.getGrandTotal(), true);

        document.add(totals);
        document.add(spacer(15));

        // ── Self-drive security deposit settlement (informational — not part of Grand Total,
        //    which is the trip fare + deductions only; the deposit was collected separately) ──
        if (dto.getDepositHeld() != null && dto.getDepositHeld() > 0) {
            PdfPTable deposit = new PdfPTable(2);
            deposit.setWidthPercentage(50);
            deposit.setHorizontalAlignment(Element.ALIGN_RIGHT);
            deposit.setWidths(new float[]{2, 1});
            addTotalRow(deposit, "Security Deposit Held", dto.getDepositHeld(), false);
            if (dto.getDepositRefunded() != null && dto.getDepositRefunded() > 0)
                addTotalRow(deposit, "Refunded to Wallet", dto.getDepositRefunded(), false);
            if (dto.getDepositAdjusted() != null && dto.getDepositAdjusted() > 0)
                addTotalRow(deposit, "Charged Beyond Deposit", dto.getDepositAdjusted(), false);
            document.add(deposit);
            document.add(spacer(10));
        }

        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            document.add(new Paragraph("Notes: " + dto.getNotes(), LABEL_FONT));
            document.add(spacer(10));
        }

        Paragraph footer = new Paragraph("Thank you for choosing RentMyRide! Har Safar, Aapke Saath.",
                new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(150, 150, 150)));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private static void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEAD_FONT));
            cell.setBackgroundColor(new Color(255, 107, 0));
            cell.setPadding(8);
            cell.setHorizontalAlignment(h.contains("Amount") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private static void addRow(PdfPTable table, String label, Double amount) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, VALUE_FONT));
        labelCell.setPadding(6);
        labelCell.setBorderColor(new Color(235, 235, 235));
        table.addCell(labelCell);

        PdfPCell amtCell = new PdfPCell(new Phrase("₹ " + fmt(amount), VALUE_FONT));
        amtCell.setPadding(6);
        amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amtCell.setBorderColor(new Color(235, 235, 235));
        table.addCell(amtCell);
    }

    private static void addTotalRow(PdfPTable table, String label, Double amount, boolean grand) {
        Font f = grand ? TOTAL_FONT : VALUE_FONT;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, f));
        labelCell.setBorder(grand ? Rectangle.TOP : Rectangle.NO_BORDER);
        labelCell.setPaddingTop(6);
        labelCell.setPaddingBottom(4);
        table.addCell(labelCell);

        PdfPCell amtCell = new PdfPCell(new Phrase("₹ " + fmt(amount), f));
        amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amtCell.setBorder(grand ? Rectangle.TOP : Rectangle.NO_BORDER);
        amtCell.setPaddingTop(6);
        amtCell.setPaddingBottom(4);
        table.addCell(amtCell);
    }

    private static PdfPCell plainCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(4);
        return cell;
    }

    private static Paragraph rightLine(String text) {
        Paragraph p = new Paragraph(text, VALUE_FONT);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private static String fmt(Double val) {
        return val == null ? "0.00" : String.format("%,.2f", val);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
