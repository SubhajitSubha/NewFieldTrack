package android.print;

/**
 * A {@link PrintDocumentAdapter.WriteResultCallback} an app can actually subclass.
 *
 * Same reason as {@link PdfLayoutCallback}: the platform's constructor is
 * package-private, and writing a PDF to a file of our own means supplying one.
 */
public abstract class PdfWriteCallback extends PrintDocumentAdapter.WriteResultCallback {
    protected PdfWriteCallback() {
        super();
    }
}
