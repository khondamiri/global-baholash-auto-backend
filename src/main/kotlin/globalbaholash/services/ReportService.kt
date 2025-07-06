package globalbaholashauto.services

import com.globalbaholash.common.AssessmentProject
import com.globalbaholash.common.AssessmentType
import com.globalbaholash.db.AssessmentProjectDocument
import com.globalbaholash.db.AssessmentRepository
import com.globalbaholash.db.AssessmentRepositoryImpl
import com.globalbaholash.db.UserRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.openxmlformats.schemas.officeDocument.x2006.math.CTR
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.collections.forEach

class ReportService (
    private val application: Application,
    private val assessmentRepository: AssessmentRepository,
    private val userRepository: UserRepository
) {
    private val templateStoragePath = application.environment.config.property("app.paths.templates").getString()
    private val projectStoragePath = application.environment.config.property("app.paths.projects").getString()

    init {
        Files.createDirectories(Paths.get(templateStoragePath))
        Files.createDirectories(Paths.get(projectStoragePath))
    }

    // =================================================================================

    // ======================= DOCUMENT GENERATION FUNCTIONALITY =======================

    suspend fun generateInitialDocuments(projectId: String, assessorId: String): List<GeneratedDocument>? {
        val project = assessmentRepository.getAssessmentProjectById(projectId, assessorId) ?: return null
        val assessmentType = assessmentRepository.getAssessmentTypeById(project.assessmentTypeId) ?: return null

        val dataMap = prepareDataMap(project, assessmentType)

        val templatesToProcess = assessmentType.templateFileNames
        if (templatesToProcess.isNullOrEmpty()) {
            application.log.error("No templates are associated with assessment type ID: ${assessmentType.id}")
            return emptyList()
        }

        val generatedDocs = mutableListOf<GeneratedDocument>()
        val projectInitialDir = Paths.get(projectStoragePath, projectId, "initial").toFile()
        projectInitialDir.mkdirs()

        templatesToProcess.forEach { templateName ->
            val templateFile = File(Paths.get(templateStoragePath, assessmentType.id, templateName).toString())
            if (!templateFile.exists()) {
                application.log.error("Template file not found: ${templateFile.absolutePath}")
                return@forEach
            }
            val outputFileName = "${project.displayName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}_${templateName}"
            val outputFile = File(projectInitialDir, outputFileName)

            fillTemplate(templateFile, outputFile, dataMap)

            val storedRelativePath = Paths.get(projectId, "initial", outputFileName).toString()
            assessmentRepository.addDocumentRecord(projectId,"INITIAL", outputFileName, storedRelativePath)

            generatedDocs.add(GeneratedDocument(outputFileName, "initial/$outputFileName"))
        }
        return generatedDocs
    }

    private fun prepareDataMap(project: AssessmentProject, type: AssessmentType): Map<String, String> {
        val dataMap = mutableMapOf<String, String>()
        val fieldDefsById = type.fieldDefinitions?.associateBy { it.id } ?: emptyMap()
        val valuesByFieldDefId = project.fieldValues.associateBy { it.fieldDefinitionId }

        fieldDefsById.values.forEach { fieldDef ->
            val enteredValue = valuesByFieldDefId[fieldDef.id]
            val displayValue = (enteredValue?.value ?: "") + (enteredValue?.values?.joinToString(", ") ?: "")

            if (displayValue.isNotBlank()) {
                dataMap[fieldDef.fieldKey] = displayValue
            } else {
                dataMap[fieldDef.fieldKey] = fieldDef.defaultTextIfEmpty ?: ""
            }
        }

        dataMap["project_display_name"] = project.displayName
        dataMap["assessment_type_id"] = project.assessmentTypeId
        dataMap["assessor_id"] = project.assessorId
        dataMap["status"] = project.status.name
        dataMap["creation_timestamp"] = project.creationTimestamp.toString()
        dataMap["last_modification_timestamp"] = project.lastModifiedTimestamp.toString()
        dataMap["public_access_id"] = project.publicAccessId.toString()
        dataMap["document_storage_path"] = project.documentStoragePath.toString()
        dataMap["qr_code_data"] = project.qrCodeData.toString()

        return dataMap
    }

    private fun fillTemplate(templateFile: File, outputFile: File, data: Map<String, String>) {
        FileOutputStream(outputFile).use { out ->
            FileInputStream(templateFile).use { fis ->
                when (templateFile.extension.lowercase()) {
                    "docx" -> {
                        val document = XWPFDocument(fis)
                        replaceTextInDoc(document, data)
                        document.write(out)
                        document.close()
                    }
                    "xlsx" -> {
                        val workbook = XSSFWorkbook(fis)
                        replaceTextInSheet(workbook, data)
                        workbook.write(out)
                        workbook.close()
                    }
                    else -> application.log.warn("Unsupported template format: ${templateFile.extension}")
                }
            }
        }
    }

    private fun replaceTextInDoc(doc: XWPFDocument, data: Map<String, String>) {
        doc.paragraphs.forEach { p -> findAndReplaceRuns(p.runs, data) }
        doc.tables.forEach { table ->
            table.rows.forEach { row ->
                row.tableCells.forEach{ cell ->
                    cell.paragraphs.forEach { p -> findAndReplaceRuns(p.runs, data) }
                }
            }
        }
    }

    private fun replaceTextInSheet(workbook: XSSFWorkbook, data: Map<String, String>) {
        val sheetIterator = workbook.sheetIterator()

        while (sheetIterator.hasNext()) {
            val sheet = sheetIterator.next()
            val rowIterator = sheet.rowIterator()
            while (rowIterator.hasNext()) {
                val row = rowIterator.next()
                val cellIterator = row.cellIterator()
                while (cellIterator.hasNext()) {
                    val cell = cellIterator.next()
                    if (cell.cellType == CellType.STRING) {
                        var cellValue = cell.richStringCellValue.string
                        if (cellValue != null && cellValue.contains("{{")) {
                            var valueWasReplaced = false

                            data.forEach { (key, value) ->
                                val placeholder = "{{$key}}"

                                if (cellValue.contains(placeholder)) {
                                    cellValue = cellValue.replace(placeholder, value)
                                    valueWasReplaced = true
                                }
                            }

                            if (valueWasReplaced) {
                                cell.setCellValue(cellValue)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findAndReplaceRuns(runs: List<XWPFRun>, data: Map<String, String>) {
        if (runs.isNullOrEmpty()) return

        val runModels = mutableListOf<RunModel>()

        runs.forEach { run ->
            val text = run.text() ?: ""
            if (text.isNotEmpty()) {
                runModels.add(RunModel(text, run.ctr.rPr as CTR?))
            }
        }

        val originalFullText = runModels.joinToString("") { it.text }

        var textAfterReplacement = originalFullText

        var foundPlaceholder = false
        data.forEach { (key, value) ->
            val placeholder = "{{$key}}"
            if (textAfterReplacement.contains(placeholder)) {
                textAfterReplacement = textAfterReplacement.replace(placeholder, value)
                foundPlaceholder = true
            }
        }

        if (!foundPlaceholder) return

        val paragraph = runs[0].parent as XWPFParagraph

        for (i in runs.indices.reversed()) {
            paragraph.removeRun(i)
        }

        val styleMap = createStyleMap(runModels)

        var currentStyle: CTR? = null
        var currentText = StringBuilder()

        textAfterReplacement.forEachIndexed { index, char ->
            val styleForChar = styleMap.getOrElse(index) { styleMap.lastOrNull() }

            if (styleForChar != currentStyle) {
                if (currentText.isNotEmpty()) {
                    val newRun = paragraph.createRun()
                    newRun.setText(currentText.toString())
                    if (currentStyle != null) {
                        newRun.ctr.rPr = currentStyle as CTRPr?
                    }
                    currentText = StringBuilder()
                }
                currentStyle = styleForChar
            }
            currentText.append(char)
        }

        if (currentText.isNotEmpty()) {
            val newRun = paragraph.createRun()
            newRun.setText(currentText.toString())
            if (currentStyle != null) {
                newRun.ctr.rPr = currentStyle as CTRPr?
            }
        }
        /*SIMPLE VERSION
        val fullText = runs.joinToString("") { it.text() }
        var textAfterReplacement = fullText

        var foundPlaceholder = false
        data.forEach { (key, value) ->
            val placeholder = "{{${key}}}"
            if (textAfterReplacement.contains(placeholder)) {
                textAfterReplacement = textAfterReplacement.replace(placeholder, value)
                foundPlaceholder = true
            }
        }

        if (!foundPlaceholder) return

        val firstRunProps = runs[0].ctr.rPr

        for (i in runs.indices.reversed()) {
            (runs[i].parent as XWPFParagraph).removeRun(i)
        }

        val newRun = (runs[0].parent as XWPFParagraph).createRun()
        newRun.setText(textAfterReplacement)

        if (firstRunProps != null) newRun.ctr.rPr = firstRunProps
        */
    }

    private fun createStyleMap(runModels: List<RunModel>): List<CTR?> {
        val styleMap = mutableListOf<CTR?>()
        runModels.forEach { model ->
            repeat(model.text.length) {
                styleMap.add((model.properties))
            }
        }

        return styleMap
    }

    // =================================================================================

    // =========================== PUBLISHING FUNCTIONALITY ============================

    suspend fun publishAssessment(projectId: String, assessorId: String, publicBaseUrl: String): PublishedDocumentInfo? {
        val project = assessmentRepository.getAssessmentProjectById(projectId, assessorId) ?: return null

        val modifiedDocs = findProjectDocuments(projectId, "MODIFIED")
        if (modifiedDocs.isEmpty()) {
            application.log.warn("Publish failed for project $projectId: No modified documents found to publish.")
            return null
        }

        val pdfFiles = mutableListOf<File>()
        val finalDocsDir = getProjectStorageDir(projectId, "final")

        try {
            for (doc in modifiedDocs) {
                val sourceFile = File(projectStoragePath, doc.storedFilePath)
                val pdfFile = convertToPdf(sourceFile, finalDocsDir)

                if (pdfFile != null) {
                    pdfFiles.add(pdfFile)
                } else {
                    application.log.error("Failed to convert ${sourceFile.name} to PDF for project $projectId.")
                    throw IOException("PDF conversion failed for one or more documents.")
                }
            }

            if (pdfFiles.isEmpty()) throw IOException("No docs were successfully converted to PDF.")

            val mergedPdfFile = mergePdfs(pdfFiles, finalDocsDir, "merged_report_${projectId}.pdf")

            val publicAccessId = project.id
            val publicLink = "$publicBaseUrl/$publicAccessId"
            val qrCodeImage = generateQrCodeImage(publicLink)

            val finalPdfWithQr = addQrCodeToPdf(mergedPdfFile, qrCodeImage, 50f, 50f)

            val relativeFinalPath = Paths.get(projectStoragePath).relativize(Paths.get(finalPdfWithQr.absolutePath)).toString()

            assessmentRepository.updateProjectPublishingInfo(projectId, publicAccessId, relativeFinalPath, publicLink)

            assessmentRepository.addDocumentRecord(projectId, "FINAL_PDF", finalPdfWithQr.name, relativeFinalPath)

            pdfFiles.forEach { it.delete() }
            mergedPdfFile.delete()

            return PublishedDocumentInfo(publicLink, finalPdfWithQr.name, relativeFinalPath)

        } catch (e: Exception) {
            application.log.error("Publishing workflow failed for project $projectId: ${e.message}", e)
            pdfFiles.forEach { it.delete() }
            return null
        }
    }

    private fun convertToPdf(sourceFile: File, outputDir: File): File? {
        if (!sourceFile.exists()) {
            application.log.error("Conversion failed: Source file does not exist at ${sourceFile.absolutePath}")
            return null
        }

        val command = listOf(
            "libreoffice",
            "--headless",
            "--convert-to",
            "pdf",
            sourceFile.absolutePath,
            "--outdir",
            outputDir.absolutePath
        )

        try {
            val process = ProcessBuilder(command).start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)

            if (finished && process.exitValue() == 0) {
                val pdfFileName = "${sourceFile.nameWithoutExtension}.pdf"
                val pdfFile = File(outputDir, pdfFileName)
                if (pdfFile.exists()) {
                    application.log.info("Successfully converted ${sourceFile.name} to PDF.")
                    return pdfFile
                }
            }
            val errors = process.errorStream.bufferedReader().readText()
            application.log.error("LibreOffice conversion failed for ${sourceFile.name}. Exit code: ${process.exitValue()}. Errors: $errors")
            return null
        } catch (e: Exception) {

            return null
        }
    }

    private fun mergePdfs(pdfsToMerge: List<File>, outputDir: File, outputFileName: String): File {
        val merger = PDFMergerUtility()
        val mergedFile = File(outputDir, outputFileName)
        merger.destinationFileName = mergedFile.absolutePath
        pdfsToMerge.forEach { pdf ->
            merger.addSource(pdf)
        }
        merger.mergeDocuments(null)
        application.log.info("Merged ${pdfsToMerge.size} PDFs into ${mergedFile.name}")
        return mergedFile
    }

    private fun generateQrCodeImage(data: String): BufferedImage {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 200, 200, hints)
        return MatrixToImageWriter.toBufferedImage(bitMatrix)
    }

    private fun addQrCodeToPdf(sourcePdfFile: File, qrCodeImage: BufferedImage, x: Float, y: Float): File {
        PDDocument.load(sourcePdfFile).use { document ->
            val firstPage = document.getPage(0)
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(qrCodeImage, "png", outputStream)
            val pdImage = PDImageXObject.createFromByteArray(document, outputStream.toByteArray(), "qr-code")

            PDPageContentStream(document, firstPage, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
                contentStream.drawImage(pdImage, x, y, pdImage.width.toFloat(), pdImage.height.toFloat())
            }
            document.save(sourcePdfFile)
        }
        application.log.info("Added QR code to ${sourcePdfFile.name}")
        return sourcePdfFile
    }

    // ========================= PUBLISHING HELPER FUNCTIONALITY ========================

    private fun getProjectStorageDir(projectId: String, subfolder: String): File {
        return Paths.get(projectStoragePath, projectId, subfolder).toFile().apply { mkdirs() }
    }

    private suspend fun findProjectDocuments(projectId: String, documentType: String): List<AssessmentProjectDocument> {
        return assessmentRepository.getDocumentsForProject(projectId, documentType)
    }
}

private data class RunModel(val text: String, val properties: CTR?)
data class GeneratedDocument(val fileName: String, val downloadPath: String)
data class PublishedDocumentInfo(val publicUrl: String, val finalFileName: String, val finalStoredPath: String)