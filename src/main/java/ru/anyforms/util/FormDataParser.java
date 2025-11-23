package ru.anyforms.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FormDataParser {
    
    public static Map<String, Object> parse(String formData) {
        Map<String, Object> result = new HashMap<>();
        
        if (formData == null || formData.isEmpty()) {
            return result;
        }
        
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                setNestedValue(result, key, value);
            }
        }
        
        return result;
    }
    
    private static void setNestedValue(Map<String, Object> map, String key, Object value) {
        // Parse keys like "leads[add][0][id]" into nested structure
        if (key.contains("[")) {
            String[] parts = key.split("\\[");
            String firstKey = parts[0];
            List<String> indices = new ArrayList<>();
            
            for (int i = 1; i < parts.length; i++) {
                String index = parts[i].replace("]", "");
                indices.add(index);
            }
            
            setNestedValueRecursive(map, firstKey, indices, 0, value);
        } else {
            map.put(key, value);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void setNestedValueRecursive(Map<String, Object> map, String key, 
                                                 List<String> indices, int indexPos, Object value) {
        if (indices.isEmpty()) {
            map.put(key, value);
            return;
        }
        
        String index = indices.get(indexPos);
        Object current = map.get(key);
        
        if (indexPos == indices.size() - 1) {
            // Last index - set the value
            if (current == null) {
                if (isNumeric(index)) {
                    // Create list and add value at index
                    List<Object> list = new ArrayList<>();
                    int listIndex = Integer.parseInt(index);
                    while (list.size() <= listIndex) {
                        list.add(null);
                    }
                    list.set(listIndex, value);
                    map.put(key, list);
                } else {
                    // Create map and set value
                    Map<String, Object> nestedMap = new HashMap<>();
                    nestedMap.put(index, value);
                    map.put(key, nestedMap);
                }
            } else if (current instanceof Map) {
                ((Map<String, Object>) current).put(index, value);
            } else if (current instanceof List) {
                List<Object> list = (List<Object>) current;
                int listIndex = Integer.parseInt(index);
                while (list.size() <= listIndex) {
                    list.add(null);
                }
                list.set(listIndex, value);
            }
        } else {
            // Intermediate index - navigate/create structure
            if (current == null) {
                if (isNumeric(index)) {
                    // Create list
                    List<Object> list = new ArrayList<>();
                    int listIndex = Integer.parseInt(index);
                    while (list.size() <= listIndex) {
                        list.add(new HashMap<String, Object>());
                    }
                    map.put(key, list);
                    Object item = list.get(listIndex);
                    if (item instanceof Map) {
                        setNestedValueRecursive((Map<String, Object>) item, indices.get(indexPos + 1), indices, indexPos + 1, value);
                    }
                } else {
                    // Create map
                    Map<String, Object> nestedMap = new HashMap<>();
                    map.put(key, nestedMap);
                    setNestedValueRecursive(nestedMap, index, indices, indexPos + 1, value);
                }
            } else if (current instanceof Map) {
                setNestedValueRecursive((Map<String, Object>) current, index, indices, indexPos + 1, value);
            } else if (current instanceof List) {
                List<Object> list = (List<Object>) current;
                int listIndex = Integer.parseInt(index);
                while (list.size() <= listIndex) {
                    list.add(new HashMap<String, Object>());
                }
                Object item = list.get(listIndex);
                if (item instanceof Map) {
                    setNestedValueRecursive((Map<String, Object>) item, indices.get(indexPos + 1), indices, indexPos + 1, value);
                }
            }
        }
    }
    
    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

