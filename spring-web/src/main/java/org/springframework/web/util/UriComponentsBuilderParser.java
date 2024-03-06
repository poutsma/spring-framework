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

import java.net.IDN;
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

	private boolean insideBrackets;

	@Nullable
	private String scheme;

	@Nullable
	private String username;

	@Nullable
	private String password;

	@Nullable
	private String host;


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
		resetState();

		while (this.pos <= this.inputLength) {
			int ch;
			if (this.pos < this.inputLength) {
				ch = this.input[this.pos];
			}
			else {
				ch = -1;
			}
			this.state.handle(ch, this);
			this.pos++;
		}
		UriComponentsBuilder result = new UriComponentsBuilder();
		if (this.scheme != null) {
			result.scheme(this.scheme);
		}
		if (this.username != null || this.password != null) {
			String userInfo = (this.username != null ? this.username : "") +
					":" +
					(this.password != null ? this.password : "");
			result.userInfo(userInfo);
		}
		if (this.host != null) {
			result.host(this.host);
		}
		return result;
	}

	private void resetState() {
		this.state = State.SCHEME_START;
		this.pos = 0;
		this.atSignSeen = false;
		this.passwordTokenSeen = false;
		this.insideBrackets = false;

		this.scheme = null;
		this.username = null;
		this.password = null;
		this.host = null;
	}

	private String parseHost(String s, boolean isOpaque) {
		if (!s.isEmpty() && s.charAt(0) == '[') {
			int lastPos = s.length() - 1;
			if (s.charAt(lastPos) != ']') {
				throwParseException("IPv6 address is missing the closing \"]\").");
			}
			String ipv6Host = s.substring(1, lastPos);
			return parseIpv6(ipv6Host);
		}
		if (isOpaque) {
			return parseOpaqueHost();
		}
		Assert.state(!s.isEmpty(), "Input should not be empty");

		String domain = UriUtils.decode(s, StandardCharsets.UTF_8);
		String asciiDomain = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);

		for (int i=0; i < asciiDomain.length(); i++) {
			char ch = asciiDomain.charAt(i);
			if (isForbiddenDomain(ch)) {
				throwParseException("Invalid character \"" + ch + "\" in domain \"" + s + "\"");
			}
		}
		char lastCh = asciiDomain.charAt(asciiDomain.length() - 1);
		if (isAsciiNumeric(lastCh)) {
			return parseIpv4(asciiDomain);
		}
		else {
			return asciiDomain;
		}
	}

	private String parseIpv6(String input) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private String parseOpaqueHost() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private String parseIpv4(String input) {
		throw new UnsupportedOperationException("Not implemented yet");
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

	private static boolean isC0Control(int ch) {
		return ch >= 0 && ch <= 0x1F;
	}

	private static boolean isNewline(int ch) {
		return ch == '\r' || ch == '\n';
	}

	private static boolean isAsciiAlpha(int ch) {
		return (ch >= 'A' && ch <= 'Z') ||
				(ch >= 'a' && ch <= 'z');
	}

	private static boolean isAsciiNumeric(int ch) {
		return (ch >= '0' && ch <= '9');
	}

	private static boolean isAsciiAlphaNumeric(int ch) {
		return isAsciiAlpha(ch) || isAsciiNumeric(ch);
	}

	private static boolean isForbiddenDomain(int ch) {
		return isForbiddenHost(ch) || isC0Control(ch) || ch == '%' || ch == 0x7F;
	}

	private static boolean isForbiddenHost(int ch) {
		return ch == 0x00 || ch == '\t' || isNewline(ch) || ch == ' ' || ch == '#' || ch == '/' || ch == ':' ||
				ch == '<' || ch == '>' || ch == '?' || ch == '@' || ch == '[' || ch == '\\' || ch == ']' || ch == '^' ||
				ch == '|';
	}


	private boolean hasSpecialScheme() {
		return "ftp".equals(this.scheme) ||
				"file".equals(this.scheme) ||
				"http".equals(this.scheme) ||
				"https".equals(this.scheme) ||
				"ws".equals(this.scheme) ||
				"wss".equals(this.scheme);
	}

	private enum State {

		SCHEME_START {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (isAsciiAlpha(ch)) {
					p.buffer.append(Character.toLowerCase((char)ch));
					p.state = SCHEME;
				}
				else {
					p.state = NO_SCHEME;
					p.pos--;
				}
			}
		},
		SCHEME {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (isAsciiAlphaNumeric(ch) || (ch == '+' || ch == '-' || ch == '.')) {
					p.buffer.append(Character.toLowerCase((char)ch));
				}
				else if (ch == ':') {
					p.scheme = p.buffer.toString();
					p.buffer.setLength(0);
					if (p.scheme.equals("file")) {
						if (p.pos >= p.inputLength - 2 ||
								p.input[p.pos + 1] != '/' ||
								p.input[p.pos + 2] != '/') {
							p.throwParseException("\"file\" scheme not followed by \"//\".");
						}
						else {
							p.state = FILE;
						}
					}
					else if (p.hasSpecialScheme()) {
						p.state = SPECIAL_AUTHORITY_SLASHES;
					}
					else if (p.pos < p.inputLength - 1 && p.input[p.pos + 1] == '/') {
						p.state = PATH_OR_AUTHORITY;
						p.pos++;
					}
					else {
						p.state = OPAQUE_PATH;
					}
				}
				else {
					p.buffer.setLength(0);
					p.state = NO_SCHEME;
					p.pos = 0;
				}

			}
		},
		NO_SCHEME {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				p.throwParseException("Missing scheme.");
			}
		},
		PATH_OR_AUTHORITY {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == '/') {
					p.state = AUTHORITY;
				}
				else {
					p.state = PATH;
					p.pos--;
				}
			}
		},
		SPECIAL_AUTHORITY_SLASHES {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == '/' && p.pos < p.inputLength - 1 && p.input[p.pos + 1] == '/') {
					p.state = SPECIAL_AUTHORITY_IGNORE_SLASHES;
					p.pos++;
				}
				else {
					p.throwParseException("Scheme \"" + p.scheme + "\" not followed by \"//\".");
				}
			}
		},
		SPECIAL_AUTHORITY_IGNORE_SLASHES {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch != '/' && ch != '\\') {
					p.state = AUTHORITY;
					p.pos--;
				}
				else {
					p.throwParseException("Scheme \"" + p.scheme + "\" not followed by \"//\".");
				}
			}
		},
		AUTHORITY {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == '@') {
					if (p.atSignSeen) {
						p.buffer.insert(0, "%40");
					}

					p.atSignSeen = true;

					int bufferLen = p.buffer.length();
					StringBuilder username = new StringBuilder(bufferLen);
					StringBuilder password = new StringBuilder(bufferLen);

					for (int i = 0; i < bufferLen; i++) {
						char authCh = p.buffer.charAt(i);
						if (authCh == ':' && !p.passwordTokenSeen) {
							p.passwordTokenSeen = true;
							continue;
						}
						String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString(authCh),
								StandardCharsets.UTF_8, HierarchicalUriComponents.Type.USER_INFO);
						if (p.passwordTokenSeen) {
							password.append(encoded);
						}
						else {
							username.append(encoded);
						}
					}
					p.username = username.toString();
					p.password = password.toString();
					p.buffer.setLength(0);
				}
				else if ((ch == -1 || ch == '/' || ch == '?' || ch == '#') ||
						(p.hasSpecialScheme() && ch == '\\')) {
					if (p.atSignSeen && p.buffer.isEmpty()) {
						p.throwParseException("Missing host.");
					}
					p.pos -= p.buffer.length() + 1;
					p.buffer.setLength(0);
					p.state = HOST;
				}
				else {
					p.buffer.append((char)ch);
				}
			}
		},
		HOST {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == ':' && !p.insideBrackets) {
					if (p.buffer.isEmpty()) {
						p.throwParseException("Missing host.");
					}
					p.parseHost(p.buffer.toString(), false);
					p.buffer.setLength(0);
					p.state = PORT;
				}
				else if ( (ch == -1 || ch == '/' || ch == '?' || ch == '#') ||
						(p.hasSpecialScheme() && ch == '\\')) {
					p.pos--;
					if (p.hasSpecialScheme() && p.buffer.isEmpty()) {
						p.throwParseException("Missing host.");
					}
					p.host = p.parseHost(p.buffer.toString(), false);
					p.buffer.setLength(0);
					p.state = PATH_START;
				}
				else {
					if (ch == '[') {
						p.insideBrackets = true;
					}
					else if (ch == ']') {
						p.insideBrackets = false;
					}
					p.buffer.append((char)ch);
				}
			}
		},
		PORT {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		FILE {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		OPAQUE_PATH {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		PATH_START {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		PATH {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		};

		public abstract void handle(int ch, UriComponentsBuilderParser parser);


	}

}
