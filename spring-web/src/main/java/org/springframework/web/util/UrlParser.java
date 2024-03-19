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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Arjen Poutsma
 * @since 6.2
 */
final class UrlParser {

	private static final int EOF = -1;

	private static final int MAX_PORT = 65535;

	private char[] input = new char[0];

	private int inputLength;

	private Charset encoding;

	@Nullable
	private final Consumer<String> validationErrorHandler;

	private int pointer;

	private final StringBuilder buffer;

	private State state = State.SCHEME_START;

	private boolean atSignSeen;

	private boolean passwordTokenSeen;

	private boolean insideBrackets;

	private String scheme = "";

	private String username = "";

	private String password = "";

	@Nullable
	private Host host;

	@Nullable
	private Integer port;

	private Path path = new PathSegments();

	private final StringBuilder query = new StringBuilder();

	private final StringBuilder fragment = new StringBuilder();


	private UrlParser(Charset encoding, @Nullable Consumer<String> validationErrorHandler) {
		this.encoding = encoding;
		this.validationErrorHandler = validationErrorHandler;
		this.buffer = new StringBuilder(this.inputLength);
	}

	public static UrlRecord parse(String input) throws InvalidUrlException {
		return parse(input, StandardCharsets.UTF_8, null);
	}

	public static UrlRecord parse(String input, @Nullable Consumer<String> validationErrorHandler) {
		return parse(input, StandardCharsets.UTF_8, validationErrorHandler);
	}

	public static UrlRecord parse(String input, Charset encoding, @Nullable Consumer<String> validationErrorHandler)
		throws InvalidUrlException {
		Assert.notNull(input, "Input must not be empty");
		Assert.notNull(encoding, "Encoding must not be null");

		UrlParser parser = new UrlParser(encoding, validationErrorHandler);

		return parser.parseInternal(input);
	}

	private UrlRecord parseInternal(String input) {
		sanitizeInput(input);

		while (this.pointer <= this.inputLength) {
			int c;
			if (this.pointer < this.inputLength) {
				c = this.input[this.pointer];
			}
			else {
				c = EOF;
			}
			this.state.handle(c, this);
			this.pointer++;
		}
		Host host = this.host != null ? this.host : EmptyHost.INSTANCE;
		String queryString = !this.query.isEmpty() ? this.query.toString() : null;
		String fragmentString = !this.fragment.isEmpty() ? this.fragment.toString() : null;

		return new UrlRecord(this.scheme,
				this.username,
				this.password,
				host,
				this.port,
				this.path,
				queryString,
				fragmentString);
	}

	void sanitizeInput(String input) {
		StringBuilder builder = new StringBuilder(input);
		boolean strip = true;
		int length = builder.length();
		for (int i = 0; i < length; i++) {
			char ch = builder.charAt(i);
			if ((strip && (ch == ' ' || isC0Control(ch)))
					|| (ch == '\t' || isNewline(ch))) {
				if (validate()) {
					// If input contains any leading (or trailing) C0 control or space, invalid-URL-unit validation error.
					// If input contains any ASCII tab or newline, invalid-URL-unit validation error.
					validationError("Code point \"" + ch + "\" is not a URL unit.");
				}
				// Remove any leading (and trailing) C0 control or space from input.
				// Remove all ASCII tab or newline from input.
				builder.deleteCharAt(i);
				length--;
				i--;
			}
			else {
				strip = false;
			}
		}
		for (int i = length - 1; i >= 0; i--) {
			char ch = builder.charAt(i);
			if (ch == ' ' || isC0Control(ch)) {
				if (validate()) {
					// If input contains any (leading or) trailing C0 control or space, invalid-URL-unit validation error.
					validationError("Code point \"" + ch + "\" is not a URL unit.");
				}
				// Remove any (leading and) trailing C0 control or space from input.
				builder.deleteCharAt(i);
				length--;
			}
			else {
				break;
			}
		}
		char[] result = new char[length];
		builder.getChars(0, length, result, 0);
		this.input = result;
		this.inputLength = result.length;
	}


	/**
	 * The host parser takes a scalar value string input with an optional
	 * boolean isOpaque (default false), and then runs these steps. They return failure or a host.
	 */
	private Host parseHost(String input, boolean isOpaque) {
		// If input starts with U+005B ([), then:
		if (!input.isEmpty() && input.charAt(0) == '[') {
			int last = input.length() - 1;
			// If input does not end with U+005D (]), IPv6-unclosed validation error, return failure.
			if (input.charAt(last) != ']') {
				failure("IPv6 address is missing the closing \"]\").");
			}
			// Return the result of IPv6 parsing input with its leading U+005B ([) and trailing U+005D (]) removed.
			String ipv6Host = input.substring(1, last);
			return new IpAddressHost(parseIpv6(ipv6Host));
		}
		// If isOpaque is true, then return the result of opaque-host parsing input.
		if (isOpaque) {
			return parseOpaqueHost();
		}
		// Assert: input is not the empty string.
		Assert.state(!input.isEmpty(), "Input should not be empty");

		// Let domain be the result of running UTF-8 decode without BOM on the percent-decoding of input.
		String domain = UriUtils.decode(input, this.encoding);
		// Let asciiDomain be the result of running domain to ASCII with domain and false.
		String asciiDomain = domainToAscii(domain, false);

		for (int i=0; i < asciiDomain.length(); i++) {
			char ch = asciiDomain.charAt(i);
			// If asciiDomain contains a forbidden domain code point, domain-invalid-code-point validation error, return failure.
			if (isForbiddenDomain(ch)) {
				failure("Invalid character \"" + ch + "\" in domain \"" + input + "\"");
			}
		}
		// If asciiDomain ends in a number, then return the result of IPv4 parsing asciiDomain.
		if (endsInNumber(asciiDomain)) {
			Ipv4Address address = parseIpv4(asciiDomain);
			return new IpAddressHost(address);
		}
		// Return asciiDomain.
		else {
			return new DomainHost(asciiDomain);
		}
	}

	/**
	 * The ends in a number checker takes an ASCII string input and then runs these steps. They return a boolean.
	 */
	private boolean endsInNumber(String input) {
		// Let parts be the result of strictly splitting input on U+002E (.).
		List<String> parts = tokenize(input, ".");
		int lastIdx = parts.size() - 1;
		// If the last item in parts is the empty string, then:
		if (parts.get(lastIdx).isEmpty()) {
			// If parts’s size is 1, then return false.
			if (parts.size() == 1) {
				return false;
			}
			// Remove the last item from parts.
			parts.remove(lastIdx);
		}
		// Let last be the last item in parts.
		String last = parts.get(parts.size() - 1);
		// If last is non-empty and contains only ASCII digits, then return true.
		if (!last.isEmpty() && containsOnlyAsciiDigits(last)) {
			return true;
		}
		// If parsing last as an IPv4 number does not return failure, then return true.
		try {
			parseIpv4Number(last);
			return true;
		}
		catch (InvalidUrlException ignored) {
		}
		// Return false.
		return false;
	}

	private Ipv4Address parseIpv4(String input) {
		// Let parts be the result of strictly splitting input on U+002E (.).
		List<String> parts = tokenize(input, ".");
		int partsSize = parts.size();
		// If the last item in parts is the empty string, then:
		if (parts.get(partsSize - 1).isEmpty()) {
			// IPv4-empty-part validation error.
			validationError("IPv4 address ends with \".\"");
			// If parts’s size is greater than 1, then remove the last item from parts.
			if (partsSize > 1) {
				parts.remove(partsSize - 1);
				partsSize--;
			}
		}
		// If parts’s size is greater than 4, IPv4-too-many-parts validation error, return failure.
		if (partsSize > 4) {
			failure("IPv4 address does not consist of exactly 4 parts.");
		}
		// Let numbers be an empty list.
		List<Integer> numbers = new ArrayList<>(partsSize);
		// For each part of parts:
		for (int i = 0; i < partsSize; i++) {
			String part = parts.get(i);
			// Let result be the result of parsing part.
			ParseIpv4NumberResult result = parseIpv4Number(part);
			if (validate() && result.validationError()) {
				validationError("The IPv4 address contains numbers expressed using hexadecimal or octal digits.");
			}
			// Append result to numbers.
			numbers.add(result.number());
		}
		for (Iterator<Integer> iterator = numbers.iterator(); iterator.hasNext();) {
			Integer number = iterator.next();
			// If any item in numbers is greater than 255, IPv4-out-of-range-part validation error.
			if (validate() && number > 255 ) {
				validationError("An IPv4 address part exceeds 255.");
			}
			if (iterator.hasNext()) {
				// If any but the last item in numbers is greater than 255, then return failure.
				if (number > 255) {
					failure("An IPv4 address part exceeds 255.");
				}
			}
			else {
				// If the last item in numbers is greater than or equal to 256^(5 − numbers’s size), then return failure.
				double limit = Math.pow(256, (5 - numbers.size()));
				if (number >= limit) {
					failure("IPv4 address part " + number + " exceeds " + limit + ".'");
				}
			}
		}
		// Let ipv4 be the last item in numbers.
		int ipv4 = numbers.get(numbers.size() - 1);
		// Remove the last item from numbers.
		numbers.remove(numbers.size() - 1);
		// Let counter be 0.
		int counter = 0;
		// For each n of numbers:
		for (Integer n : numbers) {
			// Increment ipv4 by n × 256^(3 − counter).
			int increment = n * (int) Math.pow(256, 3 - counter);
			ipv4 += increment;
			// Increment counter by 1.
			counter++;
		}
		// Return ipv4.
		return new Ipv4Address(ipv4);
	}

	/**
	 * The IPv4 number parser takes an ASCII string input and then runs these steps. They return failure or a tuple of a number and a boolean.
	 */
	private ParseIpv4NumberResult parseIpv4Number(String input) {
		// If input is the empty string, then return failure.
		if (input.isEmpty()) {
			failure(null);
		}
		// Let validationError be false.
		boolean validationError = false;
		// Let R be 10.
		int r = 10;
		int len = input.length();
		// If input contains at least two code points and the first two code points are either "0X" or "0x", then:
		if (len >= 2) {
			char ch0 = input.charAt(0);
			char ch1 = input.charAt(1);
			if (ch0 == '0' && (ch1 == 'X' || ch1 == 'x')) {
				// Set validationError to true.
				validationError = true;
				// Remove the first two code points from input.
				input = input.substring(2);
				// Set R to 16.
				r = 16;
			}
			// Otherwise, if input contains at least two code points and the first code point is U+0030 (0), then:
			else if (ch0 == '0') {
				// Set validationError to true.
				validationError = true;
				// Remove the first code point from input.
				input = input.substring(1);
				// Set R to 8.
				r = 8;
			}
		}
		// If input is the empty string, then return (0, true).
		if (input.isEmpty()) {
			return new ParseIpv4NumberResult(0, true);
		}
		try {
			// Let output be the mathematical integer value that is represented by input in radix-R notation, using ASCII hex digits for digits with values 0 through 15.
			int output = Integer.parseInt(input, r);
			// Return (output, validationError).
			return new ParseIpv4NumberResult(output, validationError);
		}
		catch (NumberFormatException ex) {
			failure(ex.getMessage());
		}
		throw new IllegalStateException();
	}

	/**
	 * The IPv6 parser takes a scalar value string input and then runs these steps. They return failure or an IPv6 address.
	 */
	private Ipv6Address parseIpv6(String input) {
		// Let address be a new IPv6 address whose IPv6 pieces are all 0.
		int[] address = new int[8];
		// Let pieceIndex be 0.
		int pieceIndex = 0;
		// Let compress be null.
		Integer compress = null;
		// Let pointer be a pointer for input.
		int pointer = 0;
		int inputLength = input.length();
		int c = (inputLength > 0) ? input.charAt(0) : EOF;
		// If c is U+003A (:), then:
		if (c == ':') {
			// If remaining does not start with U+003A (:), IPv6-invalid-compression validation error, return failure.
			if (inputLength > 1 && input.charAt(1) != ':') {
				failure("IPv6 address begins with improper compression.");
			}
			// Increase pointer by 2.
			pointer += 2;
			// Increase pieceIndex by 1 and then set compress to pieceIndex.
			pieceIndex++;
			compress = pieceIndex;
		}
		c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
		// While c is not the EOF code point:
		while (c != EOF) {
			// If pieceIndex is 8, IPv6-too-many-pieces validation error, return failure.
			if (pieceIndex == 8) {
				failure("IPv6 address contains more than 8 pieces.");
			}
			// If c is U+003A (:), then:
			if (c == ':') {
				// If compress is non-null, IPv6-multiple-compression validation error, return failure.
				if (compress != null) {
					failure("IPv6 address is compressed in more than one spot.");
				}
				// Increase pointer and pieceIndex by 1, set compress to pieceIndex, and then continue.
				pointer++;
				pieceIndex++;
				compress = pieceIndex;
				c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
				continue;
			}
			// Let value and length be 0.
			int value = 0;
			int length = 0;
			// While length is less than 4 and c is an ASCII hex digit, set value to value × 0x10 + c interpreted as hexadecimal number, and increase pointer and length by 1.
			while (length < 4 && isAsciiHexDigit(c)) {
				int cHex = Character.digit(c, 16);
				value = (value * 0x10) + cHex;
				pointer++;
				length++;
				c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
			}
			// If c is U+002E (.), then:
			if (c == '.') {
				// If length is 0, IPv4-in-IPv6-invalid-code-point validation error, return failure.
				if (length == 0) {
					failure("IPv6 address with IPv4 address syntax: IPv4 part is empty.");
				}
				// Decrease pointer by length.
				pointer -= length;
				// If pieceIndex is greater than 6, IPv4-in-IPv6-too-many-pieces validation error, return failure.
				if (pieceIndex > 6) {
					failure("IPv6 address with IPv4 address syntax: IPv6 address has more than 6 pieces.");
				}
				// Let numbersSeen be 0.
				int numbersSeen = 0;
				c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
				// While c is not the EOF code point:
				while (c != EOF) {
					// Let ipv4Piece be null.
					Integer ipv4Piece = null;
					// If numbersSeen is greater than 0, then:
					if (numbersSeen > 0) {
						// If c is a U+002E (.) and numbersSeen is less than 4, then increase pointer by 1.
						if (c =='.' && numbersSeen < 4) {
							pointer++;
							c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
						}
						// Otherwise, IPv4-in-IPv6-invalid-code-point validation error, return failure.
						else {
							failure("IPv6 address with IPv4 address syntax: " +
									"IPv4 part is empty or contains a non-ASCII digit.");
						}
					}
					// If c is not an ASCII digit, IPv4-in-IPv6-invalid-code-point validation error, return failure.
					if (!isAsciiDigit(c)) {
						failure("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
					}
					// While c is an ASCII digit:
					while (isAsciiDigit(c)) {
						// Let number be c interpreted as decimal number.
						int number = Character.digit(c, 10);
						// If ipv4Piece is null, then set ipv4Piece to number.
						if (ipv4Piece == null) {
							ipv4Piece = number;
						}
						// Otherwise, if ipv4Piece is 0, IPv4-in-IPv6-invalid-code-point validation error, return failure.
						else if (ipv4Piece == 0) {
							failure("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
						}
						// Otherwise, set ipv4Piece to ipv4Piece × 10 + number.
						else {
							ipv4Piece = ipv4Piece * 10 + number;
						}
						// If ipv4Piece is greater than 255, IPv4-in-IPv6-out-of-range-part validation error, return failure.
						if (ipv4Piece > 255) {
							failure("IPv6 address with IPv4 address syntax: IPv4 part exceeds 255.");
						}
						// Increase pointer by 1.
						pointer++;
						c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
					}
					// Set address[pieceIndex] to address[pieceIndex] × 0x100 + ipv4Piece.
					address[pieceIndex] = (byte) (address[pieceIndex] * 0x100 + (ipv4Piece != null ? ipv4Piece : 0));
					// Increase numbersSeen by 1.
					numbersSeen++;
					// If numbersSeen is 2 or 4, then increase pieceIndex by 1.
					if (numbersSeen == 2 || numbersSeen == 4) {
						pieceIndex++;
					}
					c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
				}
				// If numbersSeen is not 4, IPv4-in-IPv6-too-few-parts validation error, return failure.
				if (numbersSeen != 4) {
					failure("IPv6 address with IPv4 address syntax: IPv4 address contains too few parts.");
				}
				// Break.
				break;
			}
			// Otherwise, if c is U+003A (:):
			else if (c == ':') {
				// Increase pointer by 1.
				pointer++;
				c = (pointer < inputLength) ? input.charAt(pointer) : EOF;
				// If c is the EOF code point, IPv6-invalid-code-point validation error, return failure.
				if (c == EOF) {
					failure("IPv6 address unexpectedly ends.");
				}
			}
			// Otherwise, if c is not the EOF code point, IPv6-invalid-code-point validation error, return failure.
			else if (c != EOF) {
				failure("IPv6 address unexpectedly ends.");
			}
			// Set address[pieceIndex] to value.
			address[pieceIndex] = value;
			// Increase pieceIndex by 1.
			pieceIndex++;
		}
		// If compress is non-null, then:
		if (compress != null) {
			// Let swaps be pieceIndex − compress.
			int swaps = pieceIndex - compress;
			// Set pieceIndex to 7.
			pieceIndex = 7;
			// While pieceIndex is not 0 and swaps is greater than 0, swap address[pieceIndex] with address[compress + swaps − 1], and then decrease both pieceIndex and swaps by 1.
			while (pieceIndex != 0 && swaps > 0) {
				int tmp = address[pieceIndex];
				address[pieceIndex] = address[compress + swaps - 1];
				address[compress + swaps - 1] = tmp;
				pieceIndex--;
				swaps--;
			}
		}
		// Otherwise, if compress is null and pieceIndex is not 8, IPv6-too-few-pieces validation error, return failure.
		else if (compress == null && pieceIndex != 8) {
			failure("An uncompressed IPv6 address contains fewer than 8 pieces.");
		}
		// Return address.
		return new Ipv6Address(address);
	}

	private OpaqueHost parseOpaqueHost() {
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

	private String domainToAscii(String domain, boolean beStrict) {
		// Let result be the result of running Unicode ToASCII (https://www.unicode.org/reports/tr46/#ToASCII) with domain_name set to domain, UseSTD3ASCIIRules set to beStrict, CheckHyphens set to false, CheckBidi set to true, CheckJoiners set to true, Transitional_Processing set to false, and VerifyDnsLength set to beStrict. [UTS46]
		int flag = 0;
		if (beStrict) {
			flag |= IDN.USE_STD3_ASCII_RULES;
		}
		// Implementation note: implementing Unicode ToASCII is beyond the scope of this parser, we use java.net.IDN.toASCII
		return IDN.toASCII(domain, flag);
	}

	private boolean validate() {
		return this.validationErrorHandler != null;
	}

	private void validationError(@Nullable String additionalInfo) {
		if (this.validationErrorHandler != null) {
			StringBuilder message = new StringBuilder("URL validation error for URL [");
			message.append(this.input);
			message.append("]@");
			message.append(this.pointer);
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
		message.append(this.pointer);
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


	private boolean urlIsSpecial() {
		return "ftp".equals(this.scheme) ||
				"file".equals(this.scheme) ||
				"http".equals(this.scheme) ||
				"https".equals(this.scheme) ||
				"ws".equals(this.scheme) ||
				"wss".equals(this.scheme);
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

	private void append(char ch) {
		this.buffer.append(ch);
	}

	private void append(int ch) {
		this.buffer.append((char) ch);
	}

	private void prepend(String s) {
		this.buffer.insert(0, s);
	}

	private void emptyBuffer() {
		this.buffer.setLength(0);
	}

	private int remaining(int deltaPos) {
		int pos  = this.pointer + deltaPos;
		if (pos < this.inputLength) {
			return this.input[pos];
		}
		else {
			return EOF;
		}
	}

	/**
	 * A single-dot URL path segment is a URL path segment that is "." or an ASCII case-insensitive match for "%2e".
	 */
	private static boolean isSingleDotPathSegment(StringBuilder b) {
		int len = b.length();
		if (len == 1) {
			char ch0 = b.charAt(0);
			return ch0 == '.';
		}
		else if (len == 3) {
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

	/**
	 * A double-dot URL path segment is a URL path segment that is ".." or an ASCII case-insensitive match for ".%2e", "%2e.", or "%2e%2e".
	 */
	private static boolean isDoubleDotPathSegment(StringBuilder b) {
		int len = b.length();
		if (len == 2) {
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			return ch0 == '.' && ch1 == '.';
		}
		else if (len == 4) {
			char ch0 = b.charAt(0);
			char ch1 = b.charAt(1);
			char ch2 = b.charAt(2);
			char ch3 = b.charAt(3);
			// case-insensitive match for ".%2e" or "%2e."
			return (ch0 == '.' && ch1 == '%' && ch2 == '2' && (ch3 == 'e' || ch3 == 'E'))
				|| (ch0 == '%' && ch1 == '2' && (ch2 == 'e' || ch2 == 'E') && ch3 == '.');
		}
		else if (len == 6) {
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
			public void handle(int c, UrlParser p) {
				// If c is an ASCII alpha, append c, lowercased, to buffer, and set state to scheme state.
				if (isAsciiAlpha(c)) {
					p.append(Character.toLowerCase((char) c));
					p.state = SCHEME;
				}
				// Otherwise, set state to no scheme state and decrease pointer by 1.
				else {
					p.state = NO_SCHEME;
					p.pointer--;
				}
			}
		},
		SCHEME {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is an ASCII alphanumeric, U+002B (+), U+002D (-), or U+002E (.), append c, lowercased, to buffer.
				if (isAsciiAlphaNumeric(c) || (c == '+' || c == '-' || c == '.')) {
					p.append(Character.toLowerCase((char) c));
				}
				// Otherwise, if c is U+003A (:), then:
				else if (c == ':') {
					// Set url’s scheme to buffer.
					p.scheme = p.buffer.toString();
					// Set buffer to the empty string.
					p.emptyBuffer();
					// If url’s scheme is "file", then:
					if (p.scheme.equals("file")) {
						// If remaining does not start with "//", special-scheme-missing-following-solidus validation error.
						if (p.validate() && p.remaining(0) != '/' && p.remaining(1) != '/') {
							p.validationError("\"file\" scheme not followed by \"//\".");
						}
						// Set state to file state.
						p.state = FILE;
					}
					// Otherwise, if url is special, set state to special authority slashes state.
					else if (p.urlIsSpecial()) {
						p.state = SPECIAL_AUTHORITY_SLASHES;
					}
					// Otherwise, if remaining starts with an U+002F (/), set state to path or authority state and increase pointer by 1.
					else if (p.remaining(0) == '/') {
						p.state = PATH_OR_AUTHORITY;
						p.pointer++;
					}
					// Otherwise, set url’s path to the empty string and set state to opaque path state.
					else {
						p.path = new PathSegment("");
						p.state = OPAQUE_PATH;
					}
				}
				// Otherwise, set buffer to the empty string, state to no scheme state, and start over (from the first code point in input).
				else {
					p.emptyBuffer();
					p.state = NO_SCHEME;
					p.pointer = 0;
				}

			}
		},
		NO_SCHEME {
			@Override
			public void handle(int c, UrlParser p) {
				// If base is null, missing-scheme-non-relative-URL validation error, return failure.
				p.failure("The input is missing a scheme, because it does not begin with an ASCII alpha.");
			}
		},
		PATH_OR_AUTHORITY {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is U+002F (/), then set state to authority state.
				if (c == '/') {
					p.state = AUTHORITY;
				}
				// Otherwise, set state to path state, and decrease pointer by 1.
				else {
					p.state = PATH;
					p.pointer--;
				}
			}
		},
		SPECIAL_AUTHORITY_SLASHES {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is U+002F (/) and remaining starts with U+002F (/), then set state to special authority ignore slashes state and increase pointer by 1.
				if (c == '/' && p.remaining(0) == '/') {
					p.state = SPECIAL_AUTHORITY_IGNORE_SLASHES;
					p.pointer++;
				}
				// Otherwise, special-scheme-missing-following-solidus validation error, set state to special authority ignore slashes state and decrease pointer by 1.
				else {
					if (p.validate()) {
						p.validationError("Scheme \"" + p.scheme + "\" not followed by \"//\".");
					}
					p.state = SPECIAL_AUTHORITY_IGNORE_SLASHES;
					p.pointer--;
				}
			}
		},
		SPECIAL_AUTHORITY_IGNORE_SLASHES {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is neither U+002F (/) nor U+005C (\), then set state to authority state and decrease pointer by 1.
				if (c != '/' && c != '\\') {
					p.state = AUTHORITY;
					p.pointer--;
				}
				// Otherwise, special-scheme-missing-following-solidus validation error.
				else {
					if (p.validate()) {
						p.validationError("Scheme \"" + p.scheme + "\" not followed by \"//\".");
					}
				}
			}
		},
		AUTHORITY {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is U+0040 (@), then:
				if (c == '@') {
					// Invalid-credentials validation error.
					if (p.validate()) {
						p.validationError("Invalid credentials");
					}
					// If atSignSeen is true, then prepend "%40" to buffer.
					if (p.atSignSeen) {
						p.prepend("%40");
					}
					// Set atSignSeen to true.
					p.atSignSeen = true;

					int bufferLen = p.buffer.length();
					StringBuilder username = new StringBuilder(bufferLen);
					StringBuilder password = new StringBuilder(bufferLen);

					// For each codePoint in buffer:
					for (int i = 0; i < bufferLen; i++) {
						int codePoint = p.buffer.codePointAt(i);
						// If codePoint is U+003A (:) and passwordTokenSeen is false, then set passwordTokenSeen to true and continue.
						if (codePoint == ':' && !p.passwordTokenSeen) {
							p.passwordTokenSeen = true;
							continue;
						}
						// Let encodedCodePoints be the result of running UTF-8 percent-encode codePoint using the userinfo percent-encode set.
						String encodedCodePoints = HierarchicalUriComponents.encodeUriComponent(
								Character.toString(codePoint), p.encoding, HierarchicalUriComponents.Type.USER_INFO);
						// If passwordTokenSeen is true, then append encodedCodePoints to url’s password.
						if (p.passwordTokenSeen) {
							password.append(encodedCodePoints);
						}
						// Otherwise, append encodedCodePoints to url’s username.
						else {
							username.append(encodedCodePoints);
						}
					}
					p.username = username.toString();
					p.password = password.toString();
					// Set buffer to the empty string.
					p.emptyBuffer();
				}
				// Otherwise, if one of the following is true:
				// - c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#)
				// - url is special and c is U+005C (\)
				else if ((c == EOF || c == '/' || c == '?' || c == '#') ||
						(p.urlIsSpecial() && c == '\\')) {
					// If atSignSeen is true and buffer is the empty string, host-missing validation error, return failure.
					if (p.atSignSeen && p.buffer.isEmpty()) {
						p.failure("Missing host.");
					}
					// Decrease pointer by buffer’s code point length + 1, set buffer to the empty string, and set state to host state.
					p.pointer -= p.buffer.length() + 1;
					p.emptyBuffer();
					p.state = HOST;
				}
				// Otherwise, append c to buffer.
				else {
					p.append(c);
				}
			}
		},
		HOST {
			@Override
			public void handle(int c, UrlParser p) {
				// Otherwise, if c is U+003A (:) and insideBrackets is false, then:
				if (c == ':' && !p.insideBrackets) {
					// If buffer is the empty string, host-missing validation error, return failure.
					if (p.buffer.isEmpty()) {
						p.failure("Missing host.");
					}
					// Let host be the result of host parsing buffer with url is not special.
					Host host = p.parseHost(p.buffer.toString(), false);
					// Set url’s host to host, buffer to the empty string, and state to port state.
					p.host = host;
					p.emptyBuffer();
					p.state = PORT;
				}
				// Otherwise, if one of the following is true:
				// - c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#)
				// - url is special and c is U+005C (\)
				else if ( (c == EOF || c == '/' || c == '?' || c == '#') ||
						(p.urlIsSpecial() && c == '\\')) {
					// then decrease pointer by 1, and then:
					p.pointer--;
					// If url is special and buffer is the empty string, host-missing validation error, return failure.
					if (p.urlIsSpecial() && p.buffer.isEmpty()) {
						p.failure("The input has a special scheme, but does not contain a host.");
					}
					// Let host be the result of host parsing buffer with url is not special.
					Host host = p.parseHost(p.buffer.toString(), false);
					// Set url’s host to host, buffer to the empty string, and state to path start state.
					p.host = host;
					p.emptyBuffer();
					p.state = PATH_START;
				}
				// Otherwise:
				else {
					// If c is U+005B ([), then set insideBrackets to true.
					if (c == '[') {
						p.insideBrackets = true;
					}
					// If c is U+005D (]), then set insideBrackets to false.
					else if (c == ']') {
						p.insideBrackets = false;
					}
					// Append c to buffer.
					p.append(c);
				}
			}
		},
		PORT {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is an ASCII digit, append c to buffer.
				if (isAsciiDigit(c)) {
					p.append(c);
				}
				// Otherwise, if one of the following is true:
				// - c is the EOF code point, U+002F (/), U+003F (?), or U+0023 (#)
				// - url is special and c is U+005C (\)
				else if (c == EOF || c == '/' || c == '?' || c == '#' ||
						(p.urlIsSpecial() && c == '\\')) {
					// If buffer is not the empty string, then:
					if (!p.buffer.isEmpty()) {
						try {
							// Let port be the mathematical integer value that is represented by buffer in radix-10 using ASCII digits for digits with values 0 through 9.
							int port = Integer.parseInt(p.buffer, 0, p.buffer.length(), 10);
							// If port is greater than 2^16 − 1, port-out-of-range validation error, return failure.
							if (port > MAX_PORT) {
								p.failure("Port \"" + port + "\" is out of range");
							}
							int defaultPort = defaultPort(p.scheme);
							// Set url’s port to null, if port is url’s scheme’s default port; otherwise to port.
							if (defaultPort != -1 || port == defaultPort) {
								p.port = null;
							}
							else {
								p.port = port;
							}
							// Set buffer to the empty string.
							p.emptyBuffer();
						}
						catch (NumberFormatException ex) {
							p.failure(ex.getMessage());
						}
					}
					// Set state to path start state and decrease pointer by 1.
					p.state = PATH_START;
					p.pointer--;
				}
				// Otherwise, port-invalid validation error, return failure.
				else {
					p.failure("Invalid port: \"" + p.buffer + "\"");
				}
			}
		},
		FILE {
			@Override
			public void handle(int c, UrlParser p) {
				// Set url’s scheme to "file".
				p.scheme = "file";
				// Set url’s host to the empty string.
				p.host = EmptyHost.INSTANCE;
				// If c is U+002F (/) or U+005C (\), then:
				if (c == '/' || c == '\\') {
					// If c is U+005C (\), invalid-reverse-solidus validation error.
					if (p.validate() && c == '\\') {
						p.validationError("URL uses \\ instead of /.");
					}
					// Set state to file slash state.
					p.state = FILE_SLASH;
				}
				// Otherwise, set state to path state, and decrease pointer by 1.
				else {
					p.state = PATH;
					p.pointer--;
				}
			}
		},
		FILE_SLASH {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is U+002F (/) or U+005C (\), then:
				if (c == '/' || c == '\\') {
					// If c is U+005C (\), invalid-reverse-solidus validation error.
					if (p.validate() && c == '\\') {
						p.validationError("URL uses \\ instead of /.");
					}
					// Set state to file host state.
					p.state = FILE_HOST;
				}
				// Otherwise: Set state to path state, and decrease pointer by 1.
				else {
					p.state = PATH;
					p.pointer--;
				}
			}
		},
		FILE_HOST {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is the EOF code point, U+002F (/), U+005C (\), U+003F (?), or U+0023 (#), then decrease pointer by 1 and then:
				if (c == EOF || c == '/' || c == '\\' || c == '?' || c == '#') {
					p.pointer--;
					// If buffer is a Windows drive letter, file-invalid-Windows-drive-letter-host validation error, set state to path state.
					if (isWindowsDriveLetter(p.buffer, false)) {
						p.validationError("A file: URL’s host is a Windows drive letter.");
						p.state = PATH;
					}
					// Otherwise, if buffer is the empty string, then:
					else if (p.buffer.isEmpty()) {
						// Set url’s host to the empty string.
						p.host = EmptyHost.INSTANCE;
						// Set state to path start state.
						p.state = PATH_START;
					}
					// Otherwise, run these steps:
					else {
						// Let host be the result of host parsing buffer with url is not special.
						Host host = p.parseHost(p.buffer.toString(), false);
						// If host is "localhost", then set host to the empty string.
						if (host instanceof DomainHost domainHost && domainHost.domain().equals("localhost")) {
							host = EmptyHost.INSTANCE;
						}
						// Set url’s host to host.
						p.host = host;
						// Set buffer to the empty string and state to path start state.
						p.emptyBuffer();
						p.state = PATH_START;
					}
				}
				// Otherwise, append c to buffer.
				else {
					p.append(c);
				}
			}
		},
		PATH_START {
			@Override
			public void handle(int c, UrlParser p) {
				// If url is special, then:
				if (p.urlIsSpecial()) {
					// If c is U+005C (\), invalid-reverse-solidus validation error.
					if (p.validate() && c == '\\') {
						p.validationError("URL uses \"\\\" instead of \"/\"");
					}
					// Set state to path state.
					p.state = PATH;
					// If c is neither U+002F (/) nor U+005C (\), then decrease pointer by 1.
					if (c != '/' && c != '\\') {
						p.pointer--;
					}
				}
				// Otherwise, if c is U+003F (?), set url’s query to the empty string and state to query state.
				else if (c == '?') {
					p.query.setLength(0);
					p.state = QUERY;
				}
				// Otherwise, if c is U+0023 (#), set url’s fragment to the empty string and state to fragment state.
				else if (c =='#') {
					p.fragment.setLength(0);
					p.state = FRAGMENT;
				}
				// Otherwise, if c is not the EOF code point:
				else if (c != EOF) {
					// Set state to path state.
					p.state = PATH;
					// If c is not U+002F (/), then decrease pointer by 1.
					if (c != '/') {
						p.pointer--;
					}
				}
				else {
					throw new IllegalStateException();
				}
			}
		},
		PATH {
			@Override
			public void handle(int c, UrlParser p) {
				// If one of the following is true:
				// - c is the EOF code point or U+002F (/)
				// - url is special and c is U+005C (\)
				// - c is U+003F (?) or U+0023 (#)
				// then:
				if (c == EOF || c == '/' ||
						(p.urlIsSpecial() && c == '\\') ||
						c == '?' || c == '#') {
					// If url is special and c is U+005C (\), invalid-reverse-solidus validation error.
					if (p.validate() && p.urlIsSpecial() && c == '\\') {
						p.validationError("URL uses \"\\\" instead of \"/\"");
					}
					// If buffer is a double-dot URL path segment, then:
					if (isDoubleDotPathSegment(p.buffer)) {
						// Shorten url’s path.
						p.path.shorten(p.scheme);
						// If neither c is U+002F (/), nor url is special and c is U+005C (\), append the empty string to url’s path.
						if (c != '/' && !(p.urlIsSpecial() && c == '\\')) {
							p.path.append("");
						}
					}
					else {
						boolean singlePathSegment = isSingleDotPathSegment(p.buffer);
						// Otherwise, if buffer is a single-dot URL path segment and if neither c is U+002F (/), nor url is special and c is U+005C (\), append the empty string to url’s path.
						if (singlePathSegment && c != '/' && !(p.urlIsSpecial() && c == '\\')) {
							p.path.append("");
						}
						// Otherwise, if buffer is not a single-dot URL path segment, then:
						else if (!singlePathSegment) {
							// If url’s scheme is "file", url’s path is empty, and buffer is a Windows drive letter, then replace the second code point in buffer with U+003A (:).
							if ("file".equals(p.scheme) && p.path.isEmpty() && isWindowsDriveLetter(p.buffer, false)) {
								p.buffer.setCharAt(1, ':');
							}
							// Append buffer to url’s path.
							p.path.append(p.buffer.toString());
						}
					}
					// Set buffer to the empty string.
					p.emptyBuffer();
					// If c is U+003F (?), then set url’s query to the empty string and state to query state.
					if (c == '?') {
						p.query.setLength(0);
						p.state = QUERY;
					}
					// If c is U+0023 (#), then set url’s fragment to the empty string and state to fragment state.
					if (c == '#') {
						p.fragment.setLength(0);
						p.state = FRAGMENT;
					}
				}
				// Otherwise, run these steps:
				else {
					if (p.validate()) {
						// If c is not a URL code point and not U+0025 (%), invalid-URL-unit validation error.
						if (!isUrlCodePoint(c) && c != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
						// If c is U+0025 (%) and remaining does not start with two ASCII hex digits, invalid-URL-unit validation error.
						else if (c == '%' &&
								(p.pointer >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pointer + 1]) ||
										!isAsciiHexDigit(p.input[p.pointer + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
					}
					// UTF-8 percent-encode c using the path percent-encode set and append the result to buffer.
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) c),
							p.encoding, HierarchicalUriComponents.Type.PATH_SEGMENT);
					p.buffer.append(encoded);
				}
			}
		},
		OPAQUE_PATH {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is U+003F (?), then set url’s query to the empty string and state to query state.
				if (c == '?') {
					p.query.setLength(0);
					p.state = QUERY;
				}
				// Otherwise, if c is U+0023 (#), then set url’s fragment to the empty string and state to fragment state.
				else if (c == '#') {
					p.fragment.setLength(0);
					p.state = FRAGMENT;
				}
				// Otherwise:
				else {
					if (p.validate()) {
						// If c is not the EOF code point, not a URL code point, and not U+0025 (%), invalid-URL-unit validation error.
						if (c != EOF && !isUrlCodePoint(c) && c != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
						// If c is U+0025 (%) and remaining does not start with two ASCII hex digits, invalid-URL-unit validation error.
						else if (c == '%' &&
								(p.pointer >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pointer + 1]) ||
										!isAsciiHexDigit(p.input[p.pointer + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
					}
					// If c is not the EOF code point, UTF-8 percent-encode c using the C0 control percent-encode set and append the result to url’s path.
					if (c != EOF) {
						String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) c),
								p.encoding, HierarchicalUriComponents.Type.URI);
						p.path.append(encoded);
					}
				}
			}
		},
		QUERY {
			@Override
			public void handle(int c, UrlParser p) {
				// If encoding is not UTF-8 and one of the following is true:
				// - url is not special
				// - url’s scheme is "ws" or "wss"
				//  then set encoding to UTF-8.
				if (!p.encoding.equals(StandardCharsets.UTF_8) &&
						(!p.urlIsSpecial() || "ws".equals(p.scheme) || "wss".equals(p.scheme))) {
					p.encoding = StandardCharsets.UTF_8;
				}
				// If one of the following is true:
				// - c is U+0023 (#)
				// - c is the EOF code point
				if (c == '#' || c == EOF) {
					// Let queryPercentEncodeSet be the special-query percent-encode set if url is special; otherwise the query percent-encode set.
					// Percent-encode after encoding, with encoding, buffer, and queryPercentEncodeSet, and append the result to url’s query.
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) c),
							p.encoding, HierarchicalUriComponents.Type.QUERY);
					p.query.append(encoded);
					// Set buffer to the empty string.
					p.emptyBuffer();
					// If c is U+0023 (#), then set url’s fragment to the empty string and state to fragment state.
					if (c == '#') {
						p.fragment.setLength(0);
						p.state = FRAGMENT;
					}
				}
				// Otherwise, if c is not the EOF code point:
				else if (c != EOF) {
					if (p.validate()) {
						// If c is not a URL code point and not U+0025 (%), invalid-URL-unit validation error.
						if (!isUrlCodePoint(c) && c != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
						// If c is U+0025 (%) and remaining does not start with two ASCII hex digits, invalid-URL-unit validation error.
						else if (c == '%' &&
								(p.pointer >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pointer + 1]) ||
										!isAsciiHexDigit(p.input[p.pointer + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
					}
					// Append c to buffer.
					p.append(c);
				}
			}
		},
		FRAGMENT {
			@Override
			public void handle(int c, UrlParser p) {
				// If c is not the EOF code point, then:
				if (c != EOF) {
					if (p.validate()) {
						// If c is not a URL code point and not U+0025 (%), invalid-URL-unit validation error.
						if (!isUrlCodePoint(c) && c != '%') {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
						// If c is U+0025 (%) and remaining does not start with two ASCII hex digits, invalid-URL-unit validation error.
						else if (c == '%' &&
								(p.pointer >= p.inputLength - 2 ||
										!isAsciiHexDigit(p.input[p.pointer + 1]) ||
										!isAsciiHexDigit(p.input[p.pointer + 2]))) {
							p.validationError("Invalid URL Unit: \"" + (char) c + "\"");
						}
					}
					// UTF-8 percent-encode c using the fragment percent-encode set and append the result to url’s fragment.
					String encoded = HierarchicalUriComponents.encodeUriComponent(Character.toString((char) c),
							p.encoding, HierarchicalUriComponents.Type.FRAGMENT);
					p.fragment.append(encoded);
				}
			}
		};

		public abstract void handle(int ch, UrlParser parser);


	}

	record UrlRecord(String scheme,
					 String username,
					 String password,
					 Host host,
					 @Nullable
					 Integer port,
					 Path path,
					 @Nullable
					 String query,
					 @Nullable
					 String fragment) {

	}

	sealed interface Host permits DomainHost, EmptyHost, IpAddressHost, OpaqueHost {

	}

	static final class DomainHost implements Host {

		private final String domain;

		DomainHost(String domain) {
			this.domain = domain;
		}

		public String domain() {
			return this.domain;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			else if (o instanceof DomainHost other) {
				return this.domain.equals(other.domain);
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return this.domain.hashCode();
		}

		@Override
		public String toString() {
			return this.domain;
		}

	}

	static final class IpAddressHost implements Host {

		private final IpAddress address;

		private final String addressString;

		IpAddressHost(IpAddress address) {
			this.address = address;
			this.addressString = address.toString();
		}

		public IpAddress address() {
			return this.address;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			else if (obj instanceof IpAddressHost other) {
				return this.address.equals(other.address);
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return this.address.hashCode();
		}

		@Override
		public String toString() {
			return this.addressString;
		}
	}

	record OpaqueHost(String domain) implements Host {
	}

	static final class EmptyHost implements Host {

		static final EmptyHost INSTANCE = new EmptyHost();

		private EmptyHost() {
		}

		@Override
		public boolean equals(Object obj) {
			return obj == this || obj != null && obj.getClass() == this.getClass();
		}

		@Override
		public int hashCode() {
			return 1;
		}

		@Override
		public String toString() {
			return "";
		}

	}

	sealed interface IpAddress permits Ipv4Address, Ipv6Address {

		InetAddress inetAddress();
	}

	static final class Ipv4Address implements IpAddress {
		private final byte[] address;

		private final String string;

		Ipv4Address(int address) {
			this.address = new byte[]{
					(byte) (address >>> 24),
					(byte) (address >>> 16),
					(byte) (address >>> 8),
					(byte) (address)};
			this.string = inetAddress().getHostAddress();
		}

		@Override
		public InetAddress inetAddress() {
			try {
				return InetAddress.getByAddress(this.address);
			}
			catch (UnknownHostException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			else if (o instanceof Ipv4Address other) {
				return Arrays.equals(this.address, other.address);
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(this.address);
		}

		@Override
		public String toString() {
			return this.string;
		}
	}

	static final class Ipv6Address implements IpAddress {

		private final int[] pieces;

		private final String string;

		Ipv6Address(int[] pieces) {
			Assert.state(pieces.length == 8, "Invalid amount of IPv6 pieces");
			this.pieces = pieces;
			this.string = convertToString(compressLongestRunOfZeroes(pieces));
		}

		private static int[] compressLongestRunOfZeroes(int[] pieces) {
			int bestRunStart = -1;
			int bestRunLength = -1;
			int runStart = -1;
			for (int i = 0; i < pieces.length + 1; i++) {
				if (i < pieces.length && pieces[i] == 0) {
					if (runStart < 0) {
						runStart = i;
					}
				}
				else if (runStart >= 0) {
					int runLength = i - runStart;
					if (runLength > bestRunLength) {
						bestRunStart = runStart;
						bestRunLength = runLength;
					}
					runStart = -1;
				}
			}
			int[] result = new int[pieces.length];
			System.arraycopy(pieces, 0, result, 0, pieces.length);
			if (bestRunLength >= 2) {
				Arrays.fill(result, bestRunStart, bestRunStart + bestRunLength, -1);
			}
			return result;
		}


		private static String convertToString(int[] pieces) {
			StringBuilder builder = new StringBuilder(39);
			boolean lastWasNumber = false;
			for (int i = 0; i < pieces.length; i++) {
				boolean thisIsNumber = pieces[i] >= 0;
				if (thisIsNumber) {
					if (lastWasNumber) {
						builder.append(':');
					}
					builder.append(Integer.toHexString(pieces[i]));
				}
				else {
					if (i == 0 || lastWasNumber) {
						builder.append("::");
					}
				}
				lastWasNumber = thisIsNumber;
			}
			return builder.toString();
		}

		@Override
		public InetAddress inetAddress() {
			byte[] address = new byte[16];
			for (int i = 0; i < address.length; i = i + 2) {
				int piece = this.pieces[i / 2];
				address[i] = (byte) piece;
				address[i + 1] = (byte) (piece >>> 8);
			}
			try {
				return InetAddress.getByAddress(address);
			}
			catch (UnknownHostException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			else if (obj instanceof Ipv6Address other) {
				return Arrays.equals(this.pieces, other.pieces);
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(this.pieces);
		}

		@Override
		public String toString() {
			return this.string;
		}
	}

	sealed interface Path permits PathSegment, PathSegments {

		void append(String s);

		boolean isEmpty();

		void shorten(String scheme);
	}

	static final class PathSegment implements Path {

		private final StringBuilder segment;

		@Nullable String segmentString;

		PathSegment(String segment) {
			this.segment = new StringBuilder(segment);
		}

		public String segment() {
			String result = this.segmentString;
			if (result == null) {
				result = this.segment.toString();
				this.segmentString = result;
			}
			return result;
		}

		@Override
		public void append(String s) {
			this.segmentString = null;
			this.segment.append(s);
		}

		@Override
		public boolean isEmpty() {
			return this.segment.isEmpty();
		}

		@Override
		public void shorten(String scheme) {
			throw new IllegalStateException("Opaque path not expected");
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			else if (o instanceof PathSegment other) {
				return segment().equals(other.segment());
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return segment().hashCode();
		}

		@Override
		public String toString() {
			return segment();
		}
	}

	static final class PathSegments implements Path {

		private final List<PathSegment> segments = new ArrayList<>();

		@Override
		public void append(String segment) {
			this.segments.add(new PathSegment(segment));
		}

		public int size() {
			return this.segments.size();
		}

		public String get(int i) {
			return this.segments.get(i).segment();
		}

		@Override
		public boolean isEmpty() {
			return this.segments.isEmpty();
		}

		@Override
		public void shorten(String scheme) {
			int size = size();
			if ("file".equals(scheme) &&
					size == 1 &&
					isWindowsDriveLetter(get(0), true)) {
				return;
			}
			if (!isEmpty()) {
				this.segments.remove(size - 1);
			}
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			else if (o instanceof PathSegments other) {
				return this.segments.equals(other.segments);
			}
			else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return this.segments.hashCode();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("/");
			for (Iterator<PathSegment> iterator = this.segments.iterator(); iterator.hasNext(); ) {
				PathSegment pathSegment = iterator.next();
				builder.append(pathSegment);
				if (iterator.hasNext()) {
					builder.append('/');
				}
			}
			return builder.toString();
		}

	}

	private record ParseIpv4NumberResult(int number, boolean validationError) {

	}



}
