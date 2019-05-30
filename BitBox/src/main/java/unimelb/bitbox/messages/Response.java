package unimelb.bitbox.messages;

public abstract class Response extends Message {
    public Response(String summary) {
        super(summary);
    }

    abstract void onSent();

    @Override
    public final String networkEncode() {
        onSent();
        return toJSON().networkEncode();
    }
}
