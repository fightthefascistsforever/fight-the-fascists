package com.fightthefascists.ops;

import com.fightthefascists.common.ApiEnvelope;
import com.fightthefascists.config.RedisConfig.FtfProperties;
import com.fightthefascists.needs.NeedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
class BoardPdfService {
    private final NeedService needService;
    private final FtfProperties props;

    BoardPdfService(NeedService needService, FtfProperties props) {
        this.needService = needService;
        this.props = props;
    }

    Mono<byte[]> generate() {
        return needService.listOpen(null, null).collectList()
                .map(needs -> {
                    try (PDDocument doc = new PDDocument()) {
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                            float y = 800;
                            cs.beginText();
                            cs.setFont(bold, 16);
                            cs.newLineAtOffset(50, y);
                            cs.showText("Fight the Fascists — Supply Board");
                            cs.endText();

                            String date = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) + " IST";
                            y -= 20;
                            cs.beginText();
                            cs.setFont(font, 10);
                            cs.newLineAtOffset(50, y);
                            cs.showText(date);
                            cs.endText();

                            y -= 30;
                            if (needs.isEmpty()) {
                                cs.beginText();
                                cs.setFont(font, 12);
                                cs.newLineAtOffset(50, y);
                                cs.showText("No open needs right now.");
                                cs.endText();
                            } else {
                                for (var n : needs) {
                                    if (y < 120) break;
                                    String line = String.format("%s %s: %.0f/%.0f %s [%s]",
                                            n.zoneCode(), n.category(), n.pledged(), n.quantity(), n.unit(), n.urgency());
                                    cs.beginText();
                                    cs.setFont(font, 11);
                                    cs.newLineAtOffset(50, y);
                                    cs.showText(line.length() > 80 ? line.substring(0, 80) : line);
                                    cs.endText();
                                    y -= 16;
                                }
                            }

                            // QR code
                            BufferedImage qr = generateQr(props.publicUrl());
                            ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
                            ImageIO.write(qr, "PNG", imgBytes);
                            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, imgBytes.toByteArray(), "qr");
                            cs.drawImage(qrImage, 400, 50, 120, 120);
                            cs.beginText();
                            cs.setFont(font, 9);
                            cs.newLineAtOffset(400, 40);
                            cs.showText("Scan for live board");
                            cs.endText();
                        }

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        doc.save(out);
                        return out.toByteArray();
                    } catch (Exception e) {
                        throw new RuntimeException("PDF generation failed", e);
                    }
                });
    }

    private BufferedImage generateQr(String url) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}

@RestController
@RequestMapping("/api/v1")
class BoardController {
    private final BoardPdfService pdfService;

    BoardController(BoardPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping(value = "/board.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<byte[]> boardPdf() {
        return pdfService.generate();
    }
}

@RestController
@RequestMapping("/api/v1")
class StatsController {
    private final StatsService stats;

    StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    public Mono<ApiEnvelope<Map<String, Object>>> stats() {
        return stats.transparencyStats().map(ApiEnvelope::of);
    }
}
