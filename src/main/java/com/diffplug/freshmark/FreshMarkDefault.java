/*
 * Copyright 2015 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.freshmark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.diffplug.common.base.Errors;
import com.diffplug.jscriptbox.ScriptBox;

/** The defaault implementation. */
public class FreshMarkDefault extends FreshMark {
	private final Map<String, ?> properties;
	private final Consumer<String> warningStream;

	public FreshMarkDefault(Map<String, ?> properties, Consumer<String> warningStream) {
		this.properties = properties;
		this.warningStream = warningStream;
	}

	@Override
	protected void setupScriptEngine(String section, ScriptBox scriptBox) {
		scriptBox.setAll(properties)
				.set("link").toFunc2(FreshMarkDefault::link)
				.set("image").toFunc2(FreshMarkDefault::image)
				.set("shield").toFunc4(FreshMarkDefault::shield)
				.set("prefixDelimiterReplace").toFunc4(FreshMarkDefault::prefixDelimiterReplace);
	}

	@Override
	protected String keyToValue(String section, String key) {
		Object value = properties.get(key);
		if (value != null) {
			return Objects.toString(value);
		} else {
			warningStream.accept("Unknown key '" + key + "'");
			return key + "=UNKNOWN";
		}
	}

	////////////////////////
	// built-in functions //
	////////////////////////
	/** Generates a markdown link. */
	static String link(String text, String url) {
		return "[" + text + "](" + url + ")";
	}

	/** Generates a markdown image. */
	static String image(String altText, String url) {
		return "!" + link(altText, url);
	}

	/** Generates shields using <a href="http://shields.io/">shields.io</a>. */
	static String shield(String altText, String subject, String status, String color) {
		return image(altText, "https://img.shields.io/badge/" + shieldEscape(subject) + "-" + shieldEscape(status) + "-" + shieldEscape(color) + ".svg");
	}

	private static String shieldEscape(String raw) {
		return Errors.rethrow().get(() -> URLEncoder.encode(
				raw.replace("_", "__").replace("-", "--").replace(" ", "_"),
				StandardCharsets.UTF_8.name()));
	}

	/** Replaces after prefix and before delimiter with replacement.  */
	static String prefixDelimiterReplace(String input, String prefix, String delimiter, String replacement) {
		StringBuilder builder = new StringBuilder(input.length() * 3 / 2);
		int lastElement = 0;
		Pattern pattern = Pattern.compile("(.*?" + Pattern.quote(prefix) + ")(.*?)(" + Pattern.quote(delimiter) + ")", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(input);
		while (matcher.find()) {
			builder.append(matcher.group(1));
			builder.append(replacement);
			builder.append(matcher.group(3));
			lastElement = matcher.end();
		}
		builder.append(input.substring(lastElement));
		return builder.toString();
	}
}
