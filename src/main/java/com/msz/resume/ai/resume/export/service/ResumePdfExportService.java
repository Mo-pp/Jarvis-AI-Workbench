package com.msz.resume.ai.resume.export.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResumePdfExportService {

    private static final int MAX_HTML_LENGTH = 3_000_000;

    public byte[] export(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("html 不能为空");
        }
        if (html.length() > MAX_HTML_LENGTH) {
            throw new IllegalArgumentException("html 内容过大，无法导出");
        }

        String sanitizedHtml = sanitizeHtml(html);

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {

            Page page = browser.newPage();
            page.setViewportSize(1280, 1810);
            page.setContent(sanitizedHtml, new Page.SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));

            byte[] pdfBytes = page.pdf(new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true)
                    .setMargin(new Margin().setTop("0").setRight("0").setBottom("0").setLeft("0")));

            log.info("[ResumePdfExportService] Chromium PDF 导出成功: htmlLength={}, pdfBytes={}",
                    html.length(), pdfBytes.length);
            return pdfBytes;
        } catch (RuntimeException e) {
            throw new IllegalStateException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    private String sanitizeHtml(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings()
                .charset("UTF-8")
                .syntax(Document.OutputSettings.Syntax.html);

        ensureUtf8Meta(document);
        removeScripts(document);
        optimizeImages(document);
        return document.outerHtml();
    }

    private void ensureUtf8Meta(Document document) {
        if (document.head() == null) {
            document.prependElement("head");
        }
        if (document.head().selectFirst("meta[charset]") == null) {
            document.head().prependElement("meta").attr("charset", "UTF-8");
        }
    }

    private void removeScripts(Document document) {
        document.select("script").remove();
    }

    private void optimizeImages(Document document) {
        Elements images = document.select("img");
        for (Element image : images) {
            String existingStyle = image.attr("style");
            String exportStyle = "max-width:100%;height:auto;page-break-inside:avoid;";
            image.attr("style", mergeStyle(existingStyle, exportStyle));
            if (!image.hasAttr("alt")) {
                image.attr("alt", "resume-image");
            }
            image.removeAttr("loading");
            image.removeAttr("decoding");
        }
    }

    private String mergeStyle(String existingStyle, String appendedStyle) {
        if (existingStyle == null || existingStyle.isBlank()) {
            return appendedStyle;
        }
        String normalized = existingStyle.trim();
        if (!normalized.endsWith(";")) {
            normalized += ";";
        }
        return normalized + appendedStyle;
    }
}
