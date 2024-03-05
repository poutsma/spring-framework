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
		this.passwordTokenSeen = false;

		this.scheme = null;
		this.username = null;
		this.password = null;

		while (this.pos < this.inputLength) {
			char ch = this.input[this.pos];
			this.state.handle(ch, this);
			this.pos++;
		}
		UriComponentsBuilder result = new UriComponentsBuilder();
		if (this.scheme != null) {
			result.scheme(this.scheme);
		}
		return result;
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


	private static boolean isSpecialScheme(@Nullable String scheme) {
		return "ftp".equals(scheme) ||
				"file".equals(scheme) ||
				"http".equals(scheme) ||
				"https".equals(scheme) ||
				"ws".equals(scheme) ||
				"wss".equals(scheme);
	}

	private enum State {

		SCHEME_START {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				if (isAsciiAlpha(ch)) {
					p.buffer.append(Character.toLowerCase(ch));
					p.state = State.SCHEME;
				}
				else {
					p.state = State.NO_SCHEME;
					p.pos--;
				}
			}
		},
		SCHEME {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				if (isAsciiAlphaNumeric(ch) || (ch == '+' || ch == '-' || ch == '.')) {
					p.buffer.append(Character.toLowerCase(ch));
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
							p.state = State.FILE;
						}
					}
					else if (isSpecialScheme(p.scheme)) {
						p.state = State.SPECIAL_AUTHORITY_SLASHES;
					}
					else if (p.pos < p.inputLength - 1 && p.input[p.pos + 1] == '/') {
						p.state = State.PATH_OR_AUTHORITY;
						p.pos++;
					}
					else {
						p.state = State.OPAQUE_PATH;
					}
				}
				else {
					p.buffer.setLength(0);
					p.state = State.NO_SCHEME;
					p.pos = 0;
				}

			}
		},
		NO_SCHEME {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				p.throwParseException("Missing scheme.");
			}
		},
		PATH_OR_AUTHORITY {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				if (ch == '/') {
					p.state = State.AUTHORITY;
				}
				else {
					p.state = State.PATH;
					p.pos--;
				}
			}
		},
		SPECIAL_AUTHORITY_SLASHES {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				if (ch == '/' && p.pos < p.inputLength - 1 && p.input[p.pos + 1] == '/') {
					p.state = State.SPECIAL_AUTHORITY_IGNORE_SLASHES;
					p.pos++;
				}
				else {
					p.throwParseException("Scheme \"" + p.scheme + "\" not followed by \"//\".");
				}
			}
		},
		SPECIAL_AUTHORITY_IGNORE_SLASHES {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
				if (ch != '/' && ch != '\\') {
					p.state = State.AUTHORITY;
					p.pos--;
				}
				else {
					p.throwParseException("Scheme \"" + p.scheme + "\" not followed by \"//\".");
				}
			}
		},
		AUTHORITY {
			@Override
			public void handle(char ch, UriComponentsBuilderParser p) {
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
			}
		},
		HOST {
			@Override
			public void handle(char ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		FILE {
			@Override
			public void handle(char ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		OPAQUE_PATH {
			@Override
			public void handle(char ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		PATH {
			@Override
			public void handle(char ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		};

		public abstract void handle(char ch, UriComponentsBuilderParser parser);


	}
}
