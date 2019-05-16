package unimelb.bitbox.util;

/**
 * Simple class to manage a host string and port number. Provides conversion to and from a {@link Document}
 * which further provides conversion to a JSON string.
 * @author aaron
 *
 */
public class HostPort {
	public String host;
	public int port;
	public HostPort(String host, int port) {
		this.host=host;
		this.port=port;
	}
	public HostPort(String hostPort) {
		this.host=hostPort.split(":")[0];
		this.port=Integer.parseInt(hostPort.split(":")[1]);
	}
	public HostPort(Document hostPort) {
		this.host=hostPort.getString("host");
		this.port=(int) hostPort.getLong("port");
	}
	public Document toDoc() {
		Document hp = new Document();
		hp.append("host", host);
		hp.append("port", port);
		return hp;
	}
	public String toString() {
		return host+":"+port;
	}
	
	@Override
    public boolean equals(Object o) { 
        if (o == this) { 
            return true; 
        } 
        if (!(o instanceof HostPort)) { 
            return false; 
        } 
        HostPort c = (HostPort) o;   
        return host.equals(c.host) && port==c.port; 
    } 
}
