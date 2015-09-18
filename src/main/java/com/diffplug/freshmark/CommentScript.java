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

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.diffplug.common.base.Errors;
import com.diffplug.jscriptbox.Check;

/**
 * A CommentScript is a way of automatically generating
 * or modifying parts of a document by embedding scripts
 * in the comments of that document.
 * <p>
 * A CommentScript has the following form:
 * <pre>
 * {@code
 * [INTRON] sectionName
 * script
 * script
 * [EXON]
 * lastProgramExecutionResult
 * lastProgramExecutionResult
 * lastProgramExecutionResult
 * [INTRON] /sectionName [EXON]
 * }
 * </pre>
 * This class is a minimal implementation of a CommentScript.  To create a CommentScript,
 * you must provide:
 * <ul>
 * <li>The intron and exon strings in the constructor.</li>
 * <li>{@link #keyToValue} - defines how template keys in the script string are transformed into values</li>
 * <li>{@link #setupScriptEngine} - initializes any functions or variables which should be available to the script</li>
 * </ul>
 * See {@link FreshMark} for a sample implementation.
 */
public abstract class CommentScript {
	/**
	 * Creates a CommentScript with the given comment intron/exon pair.
	 * <p>
	 * Comment blocks will be parsed using the following regex:
	 * <pre>
	 * Pattern.quote(intron) + "(.*?)" + Pattern.quote(exon)
	 * </pre>
	 * */
	protected CommentScript(String intron, String exon) {
		this(intron, exon, Pattern.quote(intron) + "(.*?)" + Pattern.quote(exon));
	}

	/**
	 * Creates a CommentScript with the given comment intron/exon pair, as well
	 * as a custom regex.
	 * <p>
	 * Usually, you should use the {@link #CommentScript(String, String)} constructor,
	 * unless there are some special rules for how comment blocks are parsed. 
	 */
	protected CommentScript(String intron, String exon, String regex) {
		parser = new Parser(intron, exon, regex);
	}

	/** Parser which splits up the raw document into structured tags which get passed to the compiler. */
	final Parser parser;

	/** Compiles a single section/program/input combo into the appropriate output. */
	final Parser.Compiler compiler = new Parser.Compiler() {
		@Override
		public String compileSection(String section, String program, String input) {
			return Errors.rethrow().get(() -> {
				ScriptEngine engine = setupScriptEngine(section);

				// apply the templating engine to the program
				String templatedProgram = mustacheTemplate(program, key -> keyToValue(section, key));
				// populate the input data
				engine.put("input", input);
				// evaluate the program and get the result
				engine.eval(templatedProgram);
				String compiled = Check.cast(engine.get("output"), String.class);
				// make sure that the compiled output starts and ends with a newline,
				// so that the tags stay separated separated nicely
				if (!compiled.startsWith("\n")) {
					compiled = "\n" + compiled;
				}
				if (!compiled.endsWith("\n")) {
					compiled = compiled + "\n";
				}
				return parser.prefix + " " + section + "\n" +
						program +
						parser.postfix +
						compiled +
						parser.prefix + " /" + section + " " + parser.postfix;
			});
		}
	};

	/** Compiles the given input string. Input must contain only unix newlines, output is guaranteed to be the same. */
	public String compile(String input) {
		return parser.compile(input, compiler);
	}

	/** Performs templating on the script.  Delegates to mustache-style templating through keyToValue by default. */
	protected String template(String section, String script) {
		return mustacheTemplate(script, key -> keyToValue(section, key));
	}

	/** For the given section, return the templated value for the given key. */
	protected abstract String keyToValue(String section, String key);

	/**
	 * For the given section, setup a ScriptEngine appropriately.
	 * <p>
	 * The {@code input} value will be set for you, and the {@code output} value will
	 * be extracted for you, but you must do everything else.
	 */
	protected abstract ScriptEngine setupScriptEngine(String section) throws ScriptException;

	/** Replaces whatever is inside of {@code &#123;&#123;key&#125;&#125;} tags using the {@code keyToValue} function. */
	static String mustacheTemplate(String input, Function<String, String> keyToValue) {
		Matcher matcher = MUSTACHE_PATTERN.matcher(input);
		StringBuilder result = new StringBuilder(input.length() * 3 / 2);

		int lastElement = 0;
		while (matcher.find()) {
			result.append(matcher.group(1));
			result.append(keyToValue.apply(matcher.group(2)));
			lastElement = matcher.end();
		}
		result.append(input.substring(lastElement));
		return result.toString();
	}

	/** Regex which matches for {@code {{key}}}. */
	private static final Pattern MUSTACHE_PATTERN = Pattern.compile("(.*?)\\{\\{(.*?)\\}\\}", Pattern.DOTALL);
}
