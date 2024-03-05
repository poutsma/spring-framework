/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.util;

import java.nio.charset.StandardCharsets;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Arjen Poutsma
 * @since 6.2
 */
final class UriComponentsBuilderParser {

	private final char[] input;

	private final int inputLength;

	private int pos;

	private final StringBuilder buffer;

	private State state;

	private boolean atSignSeen;

	private boolean passwordTokenSeen;

	@Nullable
	private String scheme;
	@Nullable
	private String username;

	@Nullable
	private String password;


	public UriComponentsBuilderParser(String input) {
		Assert.hasLength(input, "Input must not be empty");

		this.input = sanitizeInput(input);
		this.inputLength = this.input.length;
		this.buffer = new StringBuilder(this.inputLength);
		this.state = State.SCHEME_START;
	}

	static char[] sanitizeInput(String input) {
		StringBuilder builder = new StringBuilder(input);
		boolean strip = true;
		for (int i = 0; i < builder.length(); i++) {
			char ch = builder.charAt(i);
			if ( (strip && (ch == ' ' || isC0Control(ch)))
					|| (ch == '\t' || isNewline(ch))) {
				builder.deleteCharAt(i);
				i--;
			}
			else {
				strip = false;
			}
		}
		for (int i = builder.length() - 1; i >= 0; i--) {
			char ch = builder.charAt(i);
			if (ch == ' ' || Character.isISOControl(ch)) {
				builder.deleteCharAt(i);
			}
			else {
				break;
			}
		}
		char[] result = new char[builder.length()];
		builder.getChars(0, builder.length(), result, 0);
		return result;
	}

	public UriComponentsBuilder parse() {
		this.pos = 0;
		this.atSignSeen = false;

		this.scheme = null;

		while (this.pos < this.inputLength) {
			char ch = this.input[this.pos];
			switch (this.state) {
				case SCHEME_START -> schemeStart(ch);
				case SCHEME -> scheme(ch);
				case NO_SCHEME -> throwParseException("Missing scheme.");
				case PATH_OR_AUTHORITY -> pathOrAuthority(ch);
				case SPECIAL_AUTHORITY_SLASHES -> specialAuthoritySlashes(ch);
				case SPECIAL_AUTHORITY_IGNORE_SLASHES -> specialAuthorityIgnoreSlashes(ch);
				case AUTHORITY -> authority(ch);
			}
			this.pos++;
		}
		UriComponentsBuilder result = new UriComponentsBuilder();
		if (this.scheme != null) {
			result.scheme(this.scheme);
		}
		return result;
	}

	private void schemeStart(char ch) {
		if (isAsciiAlpha(ch)) {
			this.buffer.append(Character.toLowerCase(ch));
			this.state = State.SCHEME;
		}
		else {
			this.state = State.NO_SCHEME;
			this.pos--;
		}
	}

	private void scheme(char ch) {
		if (isAsciiAlphaNumeric(ch) || (ch == '+' || ch == '-' || ch == '.')) {
			this.buffer.append(Character.toLowerCase(ch));
		}
		else if (ch == ':') {
			this.scheme = this.buffer.toString();
			this.buffer.setLength(0);
			if (this.scheme.equals("file")) {
				if (this.pos >= this.inputLength - 2 ||
						this.input[this.pos + 1] != '/' ||
						this.input[this.pos + 2] != '/') {
					throwParseException("\"file\" scheme not followed by \"//\".");
				}
				else {
					this.state = State.FILE;
				}
			}
			else if (isSpecialScheme(this.scheme)) {
				this.state = State.SPECIAL_AUTHORITY_SLASHES;
			}
			else if (this.pos < this.inputLength - 1 && this.input[this.pos + 1] == '/') {
				this.state = State.PATH_OR_AUTHORITY;
				this.pos++;
			}
			else {
				this.state = State.OPAQUE_PATH;
			}
		}
		else {
			this.buffer.setLength(0);
			this.state = State.NO_SCHEME;
			this.pos = 0;
		}
	}

	private void pathOrAuthority(char ch) {
		if (ch == '/') {
			this.state = State.AUTHORITY;
		}
		else {
			this.state = State.PATH;
			this.pos--;
		}
	}

	private void specialAuthoritySlashes(char ch) {
		if (ch == '/' && this.pos < this.inputLength - 1 && this.input[this.pos + 1] == '/') {
			this.state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
			this.pos++;
		}
		else {
			throwParseException("Scheme \"" + this.scheme + "\" not followed by \"//\".");
		}
	}

	private void specialAuthorityIgnoreSlashes(char ch) {
		if (ch != '/' && ch != '\\') {
			this.state = State.AUTHORITY;
			this.pos--;
		}
		else {
			throwParseException("Scheme \"" + this.scheme + "\" not followed by \"//\".");
		}
	}

	private void authority(char ch) {
		if (ch == '@') {
			if (this.atSignSeen) {
				this.buffer.insert(0, "%40");
			}

			this.atSignSeen = true;

			int bufferLen = this.buffer.length();
			StringBuilder username = new StringBuilder(bufferLen);
			StringBuilder password = new StringBuilder(bufferLen);

			for (int i = 0; i < bufferLen; i++) {
				char authCh = this.buffer.charAt(i);
				if (authCh == ':' && !this.passwordTokenSeen) {
					this.passwordTokenSeen = true;
					continue;
				}
				String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString(authCh),
						StandardCharsets.UTF_8, HierarchicalUriComponents.Type.USER_INFO);
				if (this.passwordTokenSeen) {
					password.append(authCh);
				}
				else {
					username.append(authCh);
				}
			}
			this.username = username.toString();
			this.password = password.toString();
			this.buffer.setLength(0);
		}
		else if (ch == '/' || ch == '?' || ch == '#' ||
				(isSpecialScheme(this.scheme) && ch == '\\')) {
			if (this.atSignSeen && this.buffer.isEmpty()) {
				throwParseException("Missing host.");
			}
			else {
				this.pos -= this.buffer.length();
				this.buffer.setLength(0);
				this.state = State.HOST;
			}
		}
		else {
			this.buffer.append(ch);
		}
	}






	private void throwParseException(@Nullable String additionalInfo) {
		StringBuilder message = new StringBuilder("Invalid URL [");
		message.append(this.input);
		message.append("] @");
		message.append(this.pos);
		if (additionalInfo != null) {
			message.append(". ");
			message.append(additionalInfo);
		}
		throw new InvalidUrlException(message.toString());
	}

	private static boolean isC0Control(char ch) {
		return ch <= 0x1F;
	}

	private static boolean isNewline(char ch) {
		return ch == '\r' || ch == '\n';
	}

	private static boolean isAsciiAlpha(char ch) {
		return (ch >= 'A' && ch <= 'Z') ||
				(ch >= 'a' && ch <= 'z');
	}

	private static boolean isAsciiAlphaNumeric(char ch) {
		return isAsciiAlpha(ch) || (ch >= '0' && ch <= '9');
	}


	private boolean isSpecialScheme(@Nullable String scheme) {
		return "ftp".equals(scheme) ||
				"file".equals(scheme) ||
				"http".equals(scheme) ||
				"https".equals(scheme) ||
				"ws".equals(scheme) ||
				"wss".equals(scheme);
	}

	private enum State {
		SCHEME_START,
		SCHEME,
		NO_SCHEME,
		PATH_OR_AUTHORITY,
		SPECIAL_AUTHORITY_SLASHES,
		SPECIAL_AUTHORITY_IGNORE_SLASHES,
		AUTHORITY,
		HOST,
		FILE,
		OPAQUE_PATH,
		PATH,

	}
}
