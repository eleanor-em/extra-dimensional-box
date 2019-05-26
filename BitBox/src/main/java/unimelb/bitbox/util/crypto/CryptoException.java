package unimelb.bitbox.util.crypto;

public class CryptoException extends Exception {
    private Exception cause;

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        System.out.println("Caused by:");
        cause.printStackTrace();
    }

    public CryptoException(Exception cause) {
        super(cause.getClass().getName() + ": " + cause.getMessage());
        this.cause = cause;
    }
}
