package com.capstone.su26_sep490_g2_be.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;

@UtilityClass
public class JsonParseUtil {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static List<String> parseStringList(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	public static String toJson(List<String> values) {
		if (values == null) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(values);
		} catch (Exception e) {
			return null;
		}
	}
}
