package unimelb.bitbox.util;

import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Helper class for using JSON. Example usage:
 * <pre>
 * {@code
 * Document doc1 = new Document();
 * doc1.append("host","localhost");
 * doc1.append("port",8111);
 * String host = doc1.getString("host");
 * int port = doc1.getInteger("port");
 * String json1 = doc1.toJson(); // convert Document to a JSON String
 * Document doc2 = Document.parse(json1); // convert JSON String back to Document
 * ArrayList<Document> docs = new ArrayList<Document>();
 * docs.add(doc1);
 * docs.add(doc2);
 * Document doc3 = new Document();
 * doc3.append("docList",docs);
 * doc3.toJson(); // {"docList":[{"host":"localhost","port":8111},{"host":"localhost","port":8111}]}
 * ArrayList<Document> docs2 = (ArrayList<Document>) doc3.get("docList");
 * }
 * </pre>
 * @author aaron
 *
 */
public class Document {
	
	protected JSONObject obj;
	
	public Document(){
		obj=new JSONObject();
	}
	
	public Document(JSONObject obj){
		this.obj = obj;
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,String val){
		if(val==null){
			obj.put(key, null);
		} else {
			obj.put(key, new String(val));
		}
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,Document doc){
		obj.put(key, doc.obj);
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,boolean val){
		obj.put(key, new Boolean(val));
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,ArrayList<?> val){
		JSONArray list = new JSONArray();
		for(Object o : val){
			if(o instanceof Document){
				list.add(((Document)o).obj);
			} else {
				list.add(o);
			}
		}
		obj.put(key,list);
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,long val){
		obj.put(key, new Long(val));
	}
	
	@SuppressWarnings("unchecked")
	public void append(String key,int val){
		obj.put(key, new Integer(val));
	}
	
	public String toJson(){
		return obj.toJSONString();
	}
	
	public static Document parse(String json) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject obj  = (JSONObject) parser.parse(json);
			return new Document(obj);
		} catch (ParseException e) {
			return new Document();
		} catch (ClassCastException e){
			return new Document();
		}
	}
	
	public boolean containsKey(String key){
		return obj.containsKey(key);
	}
	
	public String getString(String key){
		return (String) obj.get(key);
	}
	
	private ArrayList<Object> getList(JSONArray o){
		ArrayList<Object> list = new ArrayList<Object>();
		for(Object l : (JSONArray)o){
			if(l instanceof JSONObject){
				list.add(new Document((JSONObject) l));
			} else if(l instanceof JSONArray){
				list.add(getList((JSONArray) l));
			} else {
				list.add(l);
			}
		}
		return list;
	}
	
	public Object get(String key){
		Object o = obj.get(key);
		if(o instanceof JSONObject){
			return (Object) new Document((JSONObject) o);
		} else if(o instanceof JSONArray){
			return getList((JSONArray)o);
		} else {
			return o;
		}
		
	}
	
	public int getInteger(String key){
		return (int) obj.get(key);
	}
	
	public long getLong(String key){
		return (long) obj.get(key);
	}
	
	public boolean getBoolean(String key){
		return (boolean) obj.get(key);
	}
}
