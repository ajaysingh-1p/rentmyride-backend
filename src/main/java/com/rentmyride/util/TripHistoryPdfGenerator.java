package com.rentmyride.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rentmyride.dtos.RentalDTO;
import com.rentmyride.dtos.ReservationDTO;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Builds a simple tabular PDF of a customer's booking/rental history — used by the
// "Export" button on the My Bookings page (previously a CSV download).
public final class TripHistoryPdfGenerator {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(255, 107, 0));
    private static final Font SUB_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(120, 120, 120));
    private static final Font HEAD_FONT  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT  = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(30, 30, 30));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private TripHistoryPdfGenerator() {}

    public static byte[] generateReservations(List<ReservationDTO> reservations, String customerName) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        addHeader(document, "Booking History", customerName, reservations.size());

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1.8f, 1.2f, 1.2f, 2f, 2f, 1.3f, 1.2f});
        addHeaderRow(table, "Booking ID", "Car", "Pickup", "Return", "Pickup Location", "Drop Location", "Status", "Amount (₹)");

        for (ReservationDTO r : reservations) {
            addCell(table, "RES-" + r.getReservationId());
            addCell(table, safe(r.getCarBrand()) + " " + safe(r.getCarModel()));
            addCell(table, r.getPickupDate() != null ? r.getPickupDate().format(DATE_FMT) : "-");
            addCell(table, r.getReturnDate() != null ? r.getReturnDate().format(DATE_FMT) : "-");
            addCell(table, safe(r.getPickupLocation()));
            addCell(table, safe(r.getDropLocation()));
            addCell(table, r.getReservationStatus() != null ? r.getReservationStatus().toString() : "-");
            addCell(table, r.getEstimatedAmount() != null ? String.format("%,.2f", r.getEstimatedAmount()) : "-");
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    public static byte[] generateRentals(List<RentalDTO> rentals, String customerName) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        addHeader(document, "Rental History", customerName, rentals.size());

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1.8f, 1.5f, 1.5f, 1, 1.2f, 1.3f});
        addHeaderRow(table, "Rental ID", "Car", "Pickup", "Return", "KM Driven", "Status", "Total (₹)");

        for (RentalDTO r : rentals) {
            addCell(table, "RNT-" + r.getRentalId());
            addCell(table, safe(r.getCarBrand()) + " " + safe(r.getCarModel()));
            addCell(table, r.getActualPickupDatetime() != null ? r.getActualPickupDatetime().toLocalDate().format(DATE_FMT) : "-");
            addCell(table, r.getActualReturnDatetime() != null ? r.getActualReturnDatetime().toLocalDate().format(DATE_FMT) : "-");
            addCell(table, r.getTotalKmDriven() != null ? String.format("%,.0f", r.getTotalKmDriven()) : "-");
            addCell(table, r.getRentalStatus() != null ? r.getRentalStatus().toString() : "-");
            addCell(table, r.getTotalAmount() != null ? String.format("%,.2f", r.getTotalAmount()) : "-");
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private static void addHeader(Document document, String title, String customerName, int count) throws Exception {
        Paragraph brand = new Paragraph("RentMyRide", TITLE_FONT);
        document.add(brand);
        Paragraph sub = new Paragraph(title + " — " + safe(customerName) + " (" + count + " record(s))", SUB_FONT);
        sub.setSpacingAfter(15);
        document.add(sub);
    }

    private static void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEAD_FONT));
            cell.setBackgroundColor(new Color(255, 107, 0));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private static void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setPadding(5);
        cell.setBorderColor(new Color(235, 235, 235));
        table.addCell(cell);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
