package unimelb.bitbox.util.network;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.functional.algebraic.Result;

import java.util.ArrayList;
import java.util.List;

public class JSONDocument implements IJSONData {
    private JSONObject obj = new JSONObject();

    public JSONDocument() {}
    public JSONDocument(JSONObject obj) {
        this.obj = obj;
    }
    public static Result<JSONException, JSONDocument> parse(String json) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(json);
            return Result.value(new JSONDocument(obj));
        } catch (ClassCastException | NullPointerException e) {
            return Result.error(new JSONException("Error parsing JSON string `" + json + "`:\n" + e.getMessage()));
        } catch (ParseException e) {
            return Result.error(new JSONException(json, e));
        }
    }

    // Allow a limited subset of appends
    public JSONDocument append(String key, String val) {
        obj.put(key, val);
        return this;
    }
    public JSONDocument append(String key, Enum<?> val) {
        obj.put(key, val.name());
        return this;
    }
    public JSONDocument append(String key, boolean val) {
        obj.put(key, val);
        return this;
    }
    public JSONDocument append(String key, long val) {
        obj.put(key, val);
        return this;
    }
    public JSONDocument append(String key, IJSONData val) {
        obj.put(key, val.toJSON());
        return this;
    }
    public JSONDocument append(String key, List<?> val) {
        JSONArray list = new JSONArray();
        for(Object o : val){
            if(o instanceof JSONDocument){
                list.add(((JSONDocument)o).obj);
            } else {
                list.add(o);
            }
        }
        obj.put(key,list);
        return this;
    }
    public JSONDocument appendIfMissing(String key, String val) {
        if (!containsKey(key)) {
            append(key, val);
        }
        return this;
    }
    public JSONDocument appendIfMissing(String key, boolean val) {
        if (!containsKey(key)) {
            append(key, val);
        }
        return this;
    }
    public JSONDocument appendIfMissing(String key, IJSONData val) {
        if (!containsKey(key)) {
            append(key, val);
        }
        return this;
    }
    public JSONDocument appendIfMissing(String key, List<?> val) {
        if (!containsKey(key)) {
            append(key, val);
        }
        return this;
    }

    public JSONDocument join(JSONDocument other) {
        other.obj.forEach(obj::put);
        return this;
    }

    public boolean isEmpty() {
        return obj.isEmpty();
    }

    public JSONDocument toJSON(){
        return this;
    }

    public boolean containsKey(String key) {
        return obj.containsKey(key);
    }

    public <T> Result<JSONException, T> get(String key) {
        if (!containsKey(key)) {
            return Result.error(new JSONException("Field `" + key + "` missing"));
        }
        try {
            // If it's a JSONObject, Java won't allow implicit conversion constructors, so we have to deal with that
            // case separately.
            Object result = obj.get(key);
            if (result instanceof JSONObject) {
                return Result.value((T)new JSONDocument((JSONObject)result));
            }
            return Result.value((T)result);
        } catch (ClassCastException ignored) {
            return Result.error(new JSONException("Field `" + key + "` of wrong type"));
        }
    }

    public Result<JSONException, Long> getLong(String key) {
        return get(key);
    }
    public Result<JSONException, Integer> getInteger(String key) {
        return getLong(key).map(Long::intValue);
    }
    public Result<JSONException, String> getString(String key) {
        return get(key);
    }
    public Result<JSONException, Boolean> getBoolean(String key) {
        return get(key);
    }
    public Result<JSONException, JSONDocument> getJSON(String key) {
        return get(key);
    }

    public <T> Result<JSONException, List<T>> getArray(String key) {
        if (!containsKey(key)) {
            return Result.error(new JSONException("Field `" + key + "` missing"));
        }
        JSONArray jsonArray;
        try {
            jsonArray = (JSONArray) obj.get(key);
        } catch (ClassCastException ignored) {
            return Result.error(new JSONException("Field `" + key + "` of wrong type"));
        }
        try {
            ArrayList<T> res = new ArrayList<>();
            for (Object o : jsonArray) {
                if (o instanceof JSONObject) {
                    res.add((T) new JSONDocument((JSONObject) o));
                } else {
                    res.add((T) o);
                }
            }
            return Result.value(res);
        } catch (ClassCastException ignored) {
            return Result.error(new JSONException("List field `" + key + "` contains value of wrong type"));
        }
    }


    @Override
    public String toString() { return obj.toJSONString(); }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof JSONDocument && (rhs.toString().equals(toString()));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
