package android.print;

/**
 * A {@link PrintDocumentAdapter.LayoutResultCallback} an app can actually subclass.
 *
 * The platform's callback has a package-private constructor, so nothing outside
 * {@code android.print} can extend it. Driving a print adapter by hand — which is the
 * only way to get a PDF as a file rather than as a system print dialog — requires it.
 * This class exists purely to sit in the right package and open the constructor up.
 */
public abstract class PdfLayoutCallback extends PrintDocumentAdapter.LayoutResultCallback {
    protected PdfLayoutCallback() {
        super();
    }
}
