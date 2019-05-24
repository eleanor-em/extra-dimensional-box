package unimelb.bitbox.util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Optional;

public class JsonDocument {
    private JSONObject obj = new JSONObject();

    public JsonDocument() {}
    public JsonDocument(JSONObject obj) {
        this.obj = obj;
    }
    public static JsonDocument parse(String json)
        throws ResponseFormatException {
        try {
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(json);
            return new JsonDocument(obj);
        } catch (ClassCastException | NullPointerException e) {
            throw new ResponseFormatException("Error parsing JSON: " + e.getMessage());
        } catch (ParseException e) {
            throw new ResponseFormatException(e);
        }
    }

    // Allow a limited subset of appends
    public void append(String key, String val) {
        obj.put(key, val);
    }
    public void append(String key, boolean val) {
        obj.put(key, val);
    }
    public void append(String key, long val) {
        obj.put(key, val);
    }
    public void append(String key, JsonDocument val) {
        obj.put(key, val);
    }
    public void append(String key, ArrayList<?> val) {
        JSONArray list = new JSONArray();
        for(Object o : val){
            if(o instanceof JsonDocument){
                list.add(((JsonDocument)o).obj);
            } else {
                list.add(o);
            }
        }
        obj.put(key,list);
    }

    public boolean isEmpty() {
        return obj.isEmpty();
    }

    public String toJson(){
        return obj.toJSONString();
    }
    public String toString() { return toJson(); }

    public boolean containsKey(String key) {
        return obj.containsKey(key);
    }

    public <T> Optional<T> get(String key) {
        try {
            // If it's a JSONObject, Java won't allow implicit conversion constructors, so we have to deal with that
            // case separately.
            Object result = obj.get(key);
            if (result instanceof JSONObject) {
                return Optional.of((T)new JsonDocument((JSONObject)result));
            }
            // ofNullable will return `empty` if result == null, so that handles things nicely.
            return Optional.ofNullable((T)result);
        } catch (ClassCastException ignored) {
            return Optional.empty();
        }
    }

    public <T> Optional<ArrayList<T>> getArray(String key) {
        try {
            ArrayList<T> res = new ArrayList<>();
            Object array = obj.get(key);
            if (array == null) {
                return Optional.empty();
            }
            for (Object o : (JSONArray)array) {
                if (o instanceof JSONObject) {
                    res.add((T)new JsonDocument((JSONObject)o));
                } else {
                    res.add((T) o);
                }
            }
            return Optional.of(res);
        } catch (ClassCastException ignored) {
            return Optional.empty();
        }
    }

    public <T> T require(String key) throws ResponseFormatException {
        return (this.<T>get(key)).orElseThrow(() -> new ResponseFormatException("missing field: " + key));
    }

    public <T> ArrayList<T> requireArray(String key) throws ResponseFormatException {
        return (this.<T>getArray(key)).orElseThrow(() -> new ResponseFormatException("missing field: " + key));
    }
}
