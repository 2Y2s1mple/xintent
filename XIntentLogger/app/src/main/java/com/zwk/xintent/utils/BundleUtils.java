package com.zwk.xintent.utils;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public class BundleUtils {
    public static String parseBundle(Bundle bundle, int level) {
        if (bundle == null) return "Null";
        StringBuilder sb = new StringBuilder("{ ");
        try {
            Set<String> keySet = bundle.keySet();
            for(String key : keySet) {
                Object value = bundle.get(key);
                String valueClass;
                sb.append(key);
                if (value != null) {
                    valueClass = value.getClass().getName();
                    sb.append(":(").append(valueClass).append(")");
                }

                if (value == null) {
                    sb.append(":null");
                } else if (value instanceof String
                        || value instanceof Byte
                        || value instanceof Long
                        || value instanceof Integer
                        || value instanceof Boolean) {
                    sb.append(value.toString());
                } else if (value instanceof Intent) {
                    sb.append("subIntent#").append(level+1).append(":{ ");
                    Intent it = (Intent) value;
                    sb.append(it.toString());
                    Bundle xb = it.getExtras();
                    if (xb != null) {
                        sb.append(", extras=").append(parseBundle(xb, level+1));
                    }
                    sb.append(" }");
                } else if (value.getClass().isArray()) {
                    String b = printArrays(value);
                    sb.append(b);
                } else if (value instanceof ArrayList) {
                    sb.append(value.toString());
                } else if (value instanceof Bundle) {
                    sb.append("subBundle#").append(level).append(parseBundle((Bundle)value, level+1));
                } else if (value instanceof Serializable) {
                    sb.append("Serializable#" + value.toString());
                } else if (value instanceof Parcelable) {
                    sb.append("Parcelable#" + value.toString());
                }
                sb.append(", ");
            }
            sb.deleteCharAt(sb.length() - 2);
            sb.append("}");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private static Object[] toArray(Object obj) {
        int len = Array.getLength(obj);
        Object[] res = new Object[len];
        for (int i = 0; i < len; i++) res[i] = Array.get(obj, i);
        return res;
    }

    static String printArrays(Object obj) {
        int len = Array.getLength(obj);
        String s = Arrays.deepToString(new Object[] {obj});
        return len + ":" + s.substring(1, s.length() - 1);
    }
}
