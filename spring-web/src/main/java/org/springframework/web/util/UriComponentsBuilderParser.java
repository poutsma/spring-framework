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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;

import com.google.common.net.InetAddresses;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Arjen Poutsma
 * @since 6.2
 */
final class UriComponentsBuilderParser {

	private static final int MAX_PORT = 65535;

	private final char[] input;

	private final int inputLength;

	@Nullable
	private final Consumer<String> validationErrorHandler;

	private int pos;

	private final StringBuilder buffer;

	private State state;

	private boolean atSignSeen;

	private boolean passwordTokenSeen;

	private boolean insideBrackets;

	private String scheme = "";

	private String username = "";

	private String password = "";

	@Nullable
	private String host;

	@Nullable
	private Integer port;

	private final List<String> path;

	private final StringBuilder query;

	private final StringBuilder fragment;


	public UriComponentsBuilderParser(String input) {
		this(input, null);
	}

	public UriComponentsBuilderParser(String input, @Nullable Consumer<String> validationErrorHandler) {
		Assert.hasLength(input, "Input must not be empty");

		this.input = sanitizeInput(input);
		this.inputLength = this.input.length;
		this.validationErrorHandler = validationErrorHandler;
		this.buffer = new StringBuilder(this.inputLength);
		this.state = State.SCHEME_START;
		this.path = new ArrayList<>();
		this.query = new StringBuilder();
		this.fragment = new StringBuilder();
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
		result.scheme(this.scheme);
		if (!this.username.isEmpty() || !this.password.isEmpty()) {
			String userInfo = this.username + ":" + this.password;
			result.userInfo(userInfo);
		}
		if (this.host != null) {
			result.host(this.host);
		}
		if (this.port != null) {
			result.port(this.port);
		}
		if (!this.path.isEmpty()) {
			if (isSpecialScheme(this.scheme)) {
				StringBuilder pathBuilder = new StringBuilder("/");
				for (Iterator<String> iterator = this.path.iterator(); iterator.hasNext(); ) {
					String s = iterator.next();
					pathBuilder.append(s);
					if (iterator.hasNext()) {
						pathBuilder.append('/');
					}
				}
				result.path(pathBuilder.toString());
			}
			else {
				throw new UnsupportedOperationException();
			}
		}
		if (!this.query.isEmpty()) {
			result.query(this.query.toString());
		}
		if (!this.fragment.isEmpty()) {
			result.fragment(this.fragment.toString());
		}
		return result;
	}

	private void resetState() {
		this.state = State.SCHEME_START;
		this.pos = 0;
		this.atSignSeen = false;
		this.passwordTokenSeen = false;
		this.insideBrackets = false;

		this.scheme = "";
		this.username = "";
		this.password = "";
		this.host = null;
		this.port = null;
		this.path.clear();
		this.query.setLength(0);
		this.fragment.setLength(0);
	}

	private String parseHost(String s, boolean isOpaque) {
		if (!s.isEmpty() && s.charAt(0) == '[') {
			int lastPos = s.length() - 1;
			if (s.charAt(lastPos) != ']') {
				failure("IPv6 address is missing the closing \"]\").");
			}
			String ipv6Host = s.substring(1, lastPos);
			byte[] ipv6Address = parseIpv6(ipv6Host);
			return ipAddressToString(ipv6Address);
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
				failure("Invalid character \"" + ch + "\" in domain \"" + s + "\"");
			}
		}
		if (endsInNumber(asciiDomain)) {
			byte[] ipv4Address = parseIpv4(asciiDomain);
			return ipAddressToString(ipv4Address);
		}
		else {
			return asciiDomain;
		}
	}

	private static String ipAddressToString(byte[] address) {
		try {
			InetAddress address1 = InetAddress.getByAddress(address);
			return InetAddresses.toUriString(address1);
//			return address1.getHostAddress();
		}
		catch (UnknownHostException ex) {
			// should not happen because we supply a byte[]
			throw new IllegalStateException(ex);
		}
	}

	private boolean endsInNumber(String input) {
		List<String> parts = tokenize(input, ".");
		int lastPos = parts.size() - 1;
		if (parts.get(lastPos).isEmpty()) {
			if (parts.size() == 1) {
				return false;
			}
			parts.remove(lastPos);
		}
		String last = parts.get(parts.size() - 1);
		if (!last.isEmpty() && containsOnlyAsciiDigits(last)) {
			return true;
		}
		try {
			parseIpv4Number(last);
			return true;
		}
		catch (InvalidUrlException ignored) {
		}
		return false;
	}

	private byte[] parseIpv4(String input) {
		List<String> parts = tokenize(input, ".");
		int partsLen = parts.size();
		if (parts.get(partsLen - 1).isEmpty()) {
			validationError("IPv4 address ends with \".\"");
			if (partsLen > 1) {
				parts.remove(partsLen - 1);
				partsLen--;
			}
		}
		if (partsLen > 4) {
			failure("IPv4 address does not consist of exactly 4 parts.");
		}
		byte[] result = new byte[4];
		for (int i = 0; i < 4; i++) {
			int number;
			if (i < partsLen) {
				number = parseIpv4Number(parts.get(i));
			}
			else {
				number = 0;
			}
			if (number > 255) {
				failure("An IPv4 address part exceeds 255.");
			}
			result[i] = (byte) number;
		}
		return result;
	}

	private int parseIpv4Number(String input) {
		if (input.isEmpty()) {
			failure(null);
		}
		int r = 10;
		int len = input.length();
		if (len >= 2) {
			char ch0 = input.charAt(0);
			char ch1 = input.charAt(1);
			if (ch0 == '0' && (ch1 == 'X' || ch1 == 'x')) {
				validationError(null);
				input = input.substring(2);
				r = 16;
			}
			else if (ch0 == '0') {
				validationError(null);
				input = input.substring(1);
				r = 8;
			}
		}
		if (input.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(input, r);
		}
		catch (NumberFormatException ex) {
			failure(ex.getMessage());
			return -1;
		}
	}

	private byte[] parseIpv6(String input) {
		int[] address = new int[8];
		Arrays.fill(address, (byte) 0);
		int pieceIndex = 0;
		Integer compress = null;
		int inputLength = input.length();
		int pos = 0;
		int ch = (inputLength > 0) ? input.charAt(0) : -1;
		if (ch == ':') {
			if (inputLength > 1 && input.charAt(1) != ':') {
				failure("IPv6 address begins with improper compression.");
			}
			pos += 2;
			pieceIndex++;
			compress = pieceIndex;
		}
		ch = (pos < inputLength) ? input.charAt(pos) : -1;
		while (ch != -1) {
			if (pieceIndex == 8) {
				failure("IPv6 address contains more than 8 pieces.");
			}
			if (ch == ':') {
				if (compress != null) {
					failure("IPv6 address is compressed in more than one spot.");
				}
				pos++;
				pieceIndex++;
				compress = pieceIndex;
				ch = (pos < inputLength) ? input.charAt(pos) : -1;
				continue;
			}
			int value = 0;
			int length = 0;
			while (length < 4 && isAsciiHexDigit(ch)) {
				int chInt = Character.digit(ch, 16);
				value = (value * 0x10) + chInt;
				pos++;
				length++;
				ch = (pos < inputLength) ? input.charAt(pos) : -1;
			}
			if (ch == '.') {
				if (length == 0) {
					failure("IPv6 address with IPv4 address syntax: IPv4 part is empty.");
				}
				pos -= length;
				if (pieceIndex > 6) {
					failure("IPv6 address with IPv4 address syntax: IPv6 address has more than 6 pieces.");
				}
				int numbersSeen = 0;
				ch = (pos < inputLength) ? input.charAt(pos) : -1;
				while (ch != -1) {
					Integer ipv4Piece = null;
					if (numbersSeen > 0) {
						if (ch =='.' && numbersSeen < 4) {
							pos++;
						}
						else {
							failure("IPv6 address with IPv4 address syntax: " +
									"IPv4 part is empty or contains a non-ASCII digit.");
						}
					}
					ch = (pos < inputLength) ? input.charAt(pos) : -1;
					if (!isAsciiDigit(ch)) {
						failure("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
					}
					while (isAsciiDigit(ch)) {
						int number = Character.digit(ch, 10);
						if (ipv4Piece == null) {
							ipv4Piece = number;
						}
						else if (ipv4Piece == 0) {
							failure("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
						}
						else {
							ipv4Piece = ipv4Piece * 10 + number;
						}
						if (ipv4Piece > 255) {
							failure("IPv6 address with IPv4 address syntax: IPv4 part exceeds 255.");
						}
						pos++;
						ch = (pos < inputLength) ? input.charAt(pos) : -1;
					}
					address[pieceIndex] = (byte) (address[pieceIndex] * 0x100 + ((ipv4Piece != null) ? ipv4Piece : 0));
					numbersSeen++;
					if ((numbersSeen == 2) || (numbersSeen == 4)) {
						pieceIndex++;
					}
					ch = (pos < inputLength) ? input.charAt(pos) : -1;
				}
				if (numbersSeen != 4) {
					failure("IPv6 address with IPv4 address syntax: IPv4 address contains too few parts.");
				}
				break;
			}
			else if (ch == ':') {
				pos++;
				ch = (pos < inputLength) ? input.charAt(pos) : -1;
				if (ch == -1) {
					failure("IPv6 address unexpectedly ends.");
				}
			}
			else if (ch != -1) {
				failure("IPv6 address unexpectedly ends.");
			}
			address[pieceIndex] = value;
			pieceIndex++;
		}
		if (compress != null) {
			int swaps = pieceIndex - compress;
			pieceIndex = 7;
			while ((pieceIndex != 0) && (swaps > 0)) {
				int tmp = address[pieceIndex];
				address[pieceIndex] = address[compress + swaps - 1];
				address[compress + swaps - 1] = tmp;
				pieceIndex--;
				swaps--;
			}
		}
		else if (pieceIndex != 8) {
			failure("An uncompressed IPv6 address contains fewer than 8 pieces.");
		}
		byte[] result = new byte[16];
		for (int i = 0; i < result.length; i = i + 2) {
			int val = address[i / 2];
			result[i] = (byte) ((val >> 8) & 0xFF);
			result[i + 1] = (byte)(val & 0xFF);
		}
		return result;
	}

	private String parseOpaqueHost() {
		throw new UnsupportedOperationException("Not implemented yet");
	}


	private static List<String> tokenize(String str, String delimiters) {
		StringTokenizer st = new StringTokenizer(str, delimiters);
		List<String> tokens = new ArrayList<>();
		while (st.hasMoreTokens()) {
			tokens.add(st.nextToken());
		}
		return tokens;
	}

	private boolean validate() {
		return this.validationErrorHandler != null;
	}

	private void validationError(@Nullable String additionalInfo) {
		if (this.validationErrorHandler != null) {
			StringBuilder message = new StringBuilder("URL validation error for URL [");
			message.append(this.input);
			message.append("]@");
			message.append(this.pos);
			if (additionalInfo != null) {
				message.append(". ");
				message.append(additionalInfo);
			}
			this.validationErrorHandler.accept(message.toString());
		}
	}


	private void failure(@Nullable String additionalInfo) {
		StringBuilder message = new StringBuilder("URL parsing failure for URL [");
		message.append(this.input);
		message.append("] @ ");
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

	private static boolean containsOnlyAsciiDigits(String string) {
		for (int i=0; i< string.length(); i++ ) {
			char ch = string.charAt(i);
			if (!isAsciiDigit(ch)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isAsciiDigit(int ch) {
		return (ch >= '0' && ch <= '9');
	}

	private static boolean isAsciiAlphaNumeric(int ch) {
		return isAsciiAlpha(ch) || isAsciiDigit(ch);
	}

	private static boolean isAsciiHexDigit(int ch) {
		return isAsciiDigit(ch) ||
				(ch >= 'A' && ch <= 'F') ||
				(ch >= 'a' && ch <= 'f');
	}

	private static boolean isForbiddenDomain(int ch) {
		return isForbiddenHost(ch) || isC0Control(ch) || ch == '%' || ch == 0x7F;
	}

	private static boolean isForbiddenHost(int ch) {
		return ch == 0x00 || ch == '\t' || isNewline(ch) || ch == ' ' || ch == '#' || ch == '/' || ch == ':' ||
				ch == '<' || ch == '>' || ch == '?' || ch == '@' || ch == '[' || ch == '\\' || ch == ']' || ch == '^' ||
				ch == '|';
	}

	private static boolean isNonCharacter(int ch) {
		return (ch >= 0xFDD0 && ch <= 0xFDEF) || ch == 0xFFFE || ch == 0xFFFF || ch == 0x1FFFE || ch == 0x1FFFF ||
				ch == 0x2FFFE || ch == 0x2FFFF || ch == 0x3FFFE || ch == 0x3FFFF || ch == 0x4FFFE || ch == 0x4FFFF ||
				ch == 0x5FFFE || ch == 0x5FFFF || ch == 0x6FFFE || ch == 0x6FFFF || ch == 0x7FFFE || ch == 0x7FFFF ||
				ch == 0x8FFFE || ch == 0x8FFFF || ch == 0x9FFFE || ch == 0x9FFFF || ch == 0xAFFFE || ch == 0xAFFFF ||
				ch == 0xBFFFE || ch == 0xBFFFF || ch == 0xCFFFE || ch == 0xCFFFF || ch == 0xDFFFE || ch == 0xDFFFF ||
				ch == 0xEFFFE || ch == 0xEFFFF || ch == 0xFFFFE || ch == 0xFFFFF || ch == 0x10FFFE || ch == 0x10FFFF;
	}

	private static boolean isUrlCodePoint(int ch) {
		return isAsciiAlphaNumeric(ch) ||
				ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+'
				|| ch == ',' || ch == '-' || ch == '.' || ch == '/' || ch == ':' || ch == ';' || ch == '=' || ch == '?'
				|| ch == '@' || ch == '_' || ch == '~' ||
				(ch >= 0x00A0 && ch <= 0x10FFFD && !Character.isSurrogate((char) ch) && !isNonCharacter(ch));
	}


	private static boolean isSpecialScheme(@Nullable String scheme) {
		return "ftp".equals(scheme) ||
				"file".equals(scheme) ||
				"http".equals(scheme) ||
				"https".equals(scheme) ||
				"ws".equals(scheme) ||
				"wss".equals(scheme);
	}

	private static int defaultPort(@Nullable String scheme) {
		if (scheme != null) {
			return switch (scheme) {
				case "ftp" -> 21;
				case "http" -> 80;
				case "https" -> 443;
				case "ws" -> 80;
				case "wss" -> 443;
				default -> -1;
			};
		}
		else {
			return -1;
		}
	}

	private static boolean hasSinglePathSegment(StringBuilder b) {
		int bufLen = b.length();
		if (bufLen == 1) {
			char ch0 = b.charAt(0);
			return ch0 == '.';
		}
		else if (bufLen == 3) {
			//  ASCII case-insensitive match for "%2e".
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			char ch2 = b.charAt(2);
			return ch0 == '%' && ch1 == '2' && (ch2 == 'e' || ch2 == 'E');
		}
		else {
			return false;
		}
	}

	private static boolean hasDoublePathSegment(StringBuilder b) {
		int bufLen = b.length();
		if (bufLen == 2) {
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			return ch0 == '.' && ch1 == '.';
		}
		else if (bufLen == 4) {
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			char ch2 = b.charAt(2);
			char ch3 = b.charAt(3);
			// case-insensitive match for ".%2e" or "%2e."
			return (ch0 == '.' && ch1 == '%' && ch2 == '2' && (ch3 == 'e' || ch3 == 'E')) ||
					(ch0 == '%' && ch1 == '2' && (ch2 == 'e' || ch2 == 'E') && ch3 == '.');
		}
		else if (bufLen == 6) {
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			char ch2 = b.charAt(2);
			char ch3 = b.charAt(3);
			char ch4 = b.charAt(4);
			char ch5 = b.charAt(5);
			// case-insensitive match for "%2e%2e".
			return ch0 == '%' && ch1 == '2' && (ch2 == 'e' || ch2 == 'E')
				&& ch3 == '%' && ch4 == '2' && (ch5 == 'e' || ch5 == 'E');
		}
		else {
			return false;
		}
	}

	private void shortenPath() {
		Assert.state(this.state != State.OPAQUE_PATH, "OPAQUE_PATH not expected");
		if ("file".equals(this.scheme) && this.path.size() == 1 && isWindowsDriveLetter(this.path.get(0), true)) {
			return;
		}
		if (!this.path.isEmpty()) {
			this.path.remove(this.path.size() - 1);
		}
	}

	private void append(char ch) {
		this.buffer.append(ch);
	}

	private void append(int ch) {
		this.buffer.append((char) ch);
	}

	private static boolean isWindowsDriveLetter(CharSequence s, boolean normalized) {
		if (s.length() != 2) {
			return false;
		}
		char ch0 = s.charAt(0);
		if (!isAsciiAlpha(ch0)) {
			return false;
		}
		else {
			char ch1 = s.charAt(1);
			if (normalized) {
				return ch1 == ':';
			}
			else {
				return ch1 == ':' || ch1 == '|';
			}
		}
	}


	private enum State {

		SCHEME_START {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (isAsciiAlpha(ch)) {
					p.append(Character.toLowerCase((char)ch));
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
					p.append(Character.toLowerCase((char)ch));
				}
				else if (ch == ':') {
					p.scheme = p.buffer.toString();
					p.buffer.setLength(0);
					if (p.scheme.equals("file")) {
						if (p.validate() && p.pos >= p.inputLength - 2 &&
								p.input[p.pos + 1] != '/' &&
								p.input[p.pos + 2] != '/') {
							p.validationError("\"file\" scheme not followed by \"//\".");
						}
						p.state = FILE;
					}
					else if (isSpecialScheme(p.scheme)) {
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
				p.failure("Missing scheme.");
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
					if (p.validate()) {
						p.validationError("Scheme \"" + p.scheme + "\" not followed by \"//\".");
					}
					p.state = RELATIVE;
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
					if (p.validate()) {
						p.validationError("Scheme \"" + p.scheme + "\" not followed by \"//\".");
					}
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
						(isSpecialScheme(p.scheme) && ch == '\\')) {
					if (p.atSignSeen && p.buffer.isEmpty()) {
						p.failure("Missing host.");
					}
					p.pos -= p.buffer.length() + 1;
					p.buffer.setLength(0);
					p.state = HOST;
				}
				else {
					p.append(ch);
				}
			}
		},
		HOST {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == ':' && !p.insideBrackets) {
					if (p.buffer.isEmpty()) {
						p.failure("Missing host.");
					}
					p.parseHost(p.buffer.toString(), false);
					p.buffer.setLength(0);
					p.state = PORT;
				}
				else if ( (ch == -1 || ch == '/' || ch == '?' || ch == '#') ||
						(isSpecialScheme(p.scheme) && ch == '\\')) {
					p.pos--;
					if (isSpecialScheme(p.scheme) && p.buffer.isEmpty()) {
						p.failure("Missing host.");
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
					p.append(ch);
				}
			}
		},
		PORT {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (isAsciiDigit(ch)) {
					p.append(ch);
				}
				else if (ch == -1 || ch == '/' || ch == '?' || ch == '#' ||
						(isSpecialScheme(p.scheme) && ch == '\\')) {
					if (!p.buffer.isEmpty()) {
						try {
							int port = Integer.parseInt(p.buffer, 0, p.buffer.length(), 10);
							if (port > MAX_PORT) {
								p.failure("Port \"" + port + "\" is out of range");
							}
							int defaultPort = defaultPort(p.scheme);
							if (defaultPort == -1 || port != defaultPort) {
								p.port = port;
							}
							else {
								p.port = null;
							}
							p.buffer.setLength(0);
						}
						catch (NumberFormatException ex) {
							p.failure(ex.getMessage());
						}
					}
					p.state = PATH_START;
					p.pos--;
				}
				else {
					p.failure("Invalid port: \"" + p.buffer + "\"");
				}
			}
		},
		FILE {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		FILE_SLASH {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		FILE_HOST {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		},
		PATH_START {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (isSpecialScheme(p.scheme)) {
					if (p.validate() && ch == '\\') {
						p.validationError("URL uses \"\\\" instead of \"/\"");
					}
					p.state = PATH;
					if (ch != '/' && ch != '\\') {
						p.pos--;
					}
				}
				else if (ch == '?') {
					p.query.setLength(0);
					p.state = QUERY;
				}
				else if (ch =='#') {
					p.fragment.setLength(0);
					p.state = FRAGMENT;
				}
				else if (ch != -1) {
					p.state = PATH;
					if (ch != '/') {
						p.pos--;
					}
				}
				else {
					throw new IllegalStateException();
				}
			}
		},
		PATH {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == -1 || ch == '/' ||
						(isSpecialScheme(p.scheme) && ch == '\\') ||
						ch == '?' || ch == '#') {
					if (p.validate() && isSpecialScheme(p.scheme) && ch == '\\') {
						p.validationError("URL uses \"\\\" instead of \"/\"");
					}
					if (hasDoublePathSegment(p.buffer)) {
						p.shortenPath();
						if (ch != '/' && !(isSpecialScheme(p.scheme) && ch == '\\')) {
							p.path.add("");
						}
					}
					else {
						boolean singlePathSegment = hasSinglePathSegment(p.buffer);
						if (singlePathSegment && ch != '/' && !(isSpecialScheme(p.scheme) && ch == '\\')) {
							p.path.add("");
						}
						else if (!singlePathSegment) {
							if ("file".equals(p.scheme) && p.path.isEmpty() && isWindowsDriveLetter(p.buffer, false)) {
								p.buffer.setCharAt(1, ':');
							}
							p.path.add(p.buffer.toString());
						}
					}
					p.buffer.setLength(0);
					if (ch == '?') {
						p.query.setLength(0);
						p.state = QUERY;
					}
					if (ch == '#') {
						p.fragment.setLength(0);
						p.state = FRAGMENT;
					}
				}
				else {
					if (p.validate()) {
						if (!isUrlCodePoint(ch) && ch != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
						else if (ch == '%' &&
								(p.pos >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pos + 1]) ||
										!isAsciiHexDigit(p.input[p.pos + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
					}
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) ch),
							StandardCharsets.UTF_8, HierarchicalUriComponents.Type.PATH_SEGMENT);
					p.buffer.append(encoded);
				}
			}
		},
		OPAQUE_PATH {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == '?') {
					p.query.setLength(0);
					p.state = QUERY;
				}
				else if (ch == '#') {
					p.fragment.setLength(0);
					p.state = FRAGMENT;
				}
				else {
					if (p.validate()) {
						if (ch != -1 && !isUrlCodePoint(ch) && ch != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
						else if (ch == '%' &&
								(p.pos >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pos + 1]) ||
										!isAsciiHexDigit(p.input[p.pos + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
					}
					if (ch != -1) {
						String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) ch),
								StandardCharsets.UTF_8, HierarchicalUriComponents.Type.URI);
						if (p.path.isEmpty()) {
							p.path.add(encoded);
						}
						else {
							String current = p.path.get(0);
							p.path.set(0, current + encoded);
						}
					}
				}
			}
		},
		QUERY {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch == '#' || ch == -1) {
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) ch),
							StandardCharsets.UTF_8, HierarchicalUriComponents.Type.QUERY);
					p.query.append(encoded);
					p.buffer.setLength(0);
					if (ch == '#') {
						p.fragment.setLength(0);
						p.state = FRAGMENT;
					}
				}
				else if (ch != -1) {
					if (p.validate()) {
						if (!isUrlCodePoint(ch) && ch != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
						else if (ch == '%' &&
								(p.pos >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pos + 1]) ||
										!isAsciiHexDigit(p.input[p.pos + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
					}
					p.append(ch);
				}
			}
		},
		FRAGMENT {
			@Override
			public void handle(int ch, UriComponentsBuilderParser p) {
				if (ch != -1) {
					if (p.validate()) {
						if (!isUrlCodePoint(ch) && ch != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
						else if (ch == '%' &&
								(p.pos >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pos + 1]) ||
										!isAsciiHexDigit(p.input[p.pos + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) ch + "\"");
						}
					}
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) ch),
							StandardCharsets.UTF_8, HierarchicalUriComponents.Type.FRAGMENT);
					p.fragment.append(encoded);
				}
			}
		},
		RELATIVE {
			@Override
			public void handle(int ch, UriComponentsBuilderParser parser) {
				throw new UnsupportedOperationException();
			}
		};

		public abstract void handle(int ch, UriComponentsBuilderParser parser);


	}


}
