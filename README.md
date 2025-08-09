# Global Baholash - Backend Service

This repository contains the source code for the backend service powering the Global Baholash suite of applications. It is a robust, secure, and scalable server built with Kotlin and Ktor, designed to handle all core business logic, data persistence, and document processing for the vehicle assessment workflow.

The backend exposes a comprehensive RESTful API consumed by various clients, including the Assessor Desktop App and the Admin Desktop App.

---

## 🚀 Core Features & Functionality

This backend service is the central nervous system of the Global Baholash ecosystem, providing the following capabilities through its API:

### User & Authentication Management
-   **Secure Registration & Login:** Endpoints for assessor and admin registration with password hashing (bcrypt) and login that returns a secure JSON Web Token (JWT).
-   **Email Verification:** A complete workflow for sending verification links to new users' emails and confirming ownership. Login is restricted until an email is verified.
-   **Password Reset:** A secure, token-based flow for users who have forgotten their password, including email sending and token validation.
-   **Role-Based Access Control (RBAC):** Differentiates between `ADMIN` and `ASSESSOR` roles. API endpoints are protected to ensure users can only perform actions permitted by their role.

### Administrative Capabilities
-   **Assessor Management:** Admin-only APIs to:
    -   List all assessor accounts.
    -   View detailed profiles of individual assessors (excluding sensitive data).
    -   Activate and deactivate assessor accounts.
-   **Credit System:** Admins can assign and update "assessment credits" to assessors. The system enforces that an assessment can only be created if the assessor has sufficient credits.
-   **Dynamic Assessment Type Management:**
    -   Admins can create, update, and delete different *types* of assessments (e.g., "Standard Collision," "Minor Cosmetic Damage").
    -   Each assessment type is defined with a fully dynamic set of fields, including labels, data types (text, number, boolean, dropdowns), validation rules (`isRequired`), and configurable default text for empty fields.
    -   Admins upload specific `.docx` and `.xlsx` document templates during the creation of an assessment type.
-   **Assessor-Type Assignment:** Admins can assign specific assessment types to individual assessors, ensuring assessors only have access to the forms relevant to their duties.

### Assessment Workflow Management
-   **Assessment Project CRUD:** Secure endpoints for authenticated assessors to create, read, update, and delete their assessment projects based on their assigned assessment types.
-   **Server-Side Document Generation:**
    -   An endpoint to dynamically generate initial report documents (`.docx`, `.xlsx`) by populating the type-specific templates with data from a given assessment project.
    -   Utilizes a robust placeholder replacement algorithm to handle complex documents.
-   **File Storage & Handling:**
    -   Securely handles multipart file uploads for assessors to submit their manually modified documents.
    -   Manages storage of templates, generated documents, and user-uploaded files in a structured file system.
-   **Publishing & PDF Workflow:**
    -   A dedicated endpoint that orchestrates the entire publishing process:
        1.  **PDF Conversion:** Converts uploaded `.docx` and `.xlsx` files to PDF using a high-fidelity external tool (e.g., headless LibreOffice).
        2.  **PDF Merging:** Combines multiple generated PDFs into a single, final report.
        3.  **QR Code Generation:** Creates a unique QR code pointing to the public access page for the report.
        4.  **PDF Embedding:** Embeds the generated QR code onto the first page of the final PDF report.
-   **Public Document Access:**
    -   Generates a unique, secure public access ID for each published assessment.
    -   Provides an (unprotected) endpoint to serve metadata and a download link for the final, published PDF report, enabling easy sharing with clients.

---

## 🛠️ Technology Stack

-   **Language:** [Kotlin](https://kotlinlang.org/)
-   **Framework:** [Ktor](https://ktor.io/) (asynchronous, coroutine-based)
-   **Asynchronous Programming:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
-   **Database:** [PostgreSQL](https://www.postgresql.org/)
-   **Data Access:** [Exposed (Kotlin SQL Framework)](https://github.com/JetBrains/Exposed)
-   **JSON Serialization:** [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
-   **Authentication:** [JWT (JSON Web Tokens)](https://jwt.io/)
-   **Password Hashing:** jBCrypt
-   **Document Processing:** [Apache POI](https://poi.apache.org/) (for `.docx` & `.xlsx`)
-   **PDF Handling:** [Apache PDFBox](https://pdfbox.apache.org/) (for merging & modification)
-   **PDF Conversion:** External dependency on [LibreOffice](https://www.libreoffice.org/) (headless mode)
-   **QR Code Generation:** [ZXing ("Zebra Crossing")](https://github.com/zxing/zxing)
-   **Email Service:** Jakarta Mail (JavaMail)
-   **Build System:** Gradle Kotlin DSL
