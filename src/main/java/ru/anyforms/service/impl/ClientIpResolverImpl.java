package ru.anyforms.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.anyforms.service.ClientIpResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
@Service
class ClientIpResolverImpl implements ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final List<Cidr> trustedProxies;

    ClientIpResolverImpl(@Value("${http.trusted-proxies}") String trustedProxies) {
        this.trustedProxies = parseCidrs(trustedProxies);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalize(hops[i]);
            InetAddress address = parseLiteral(hop);
            if (address == null) {
                continue;
            }
            if (!isTrustedProxy(address)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String literal) {
        InetAddress address = parseLiteral(normalize(literal));
        return address != null && isTrustedProxy(address);
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(address));
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : "";
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        int colon = value.indexOf(':');
        if (colon > 0 && value.indexOf(':', colon + 1) < 0 && IPV4.matcher(value.substring(0, colon)).matches()) {
            return value.substring(0, colon);
        }
        return value;
    }

    static InetAddress parseLiteral(String literal) {
        if (literal == null || literal.isEmpty()) {
            return null;
        }
        boolean ipv4 = IPV4.matcher(literal).matches() && octetsInRange(literal);
        boolean ipv6 = !ipv4 && literal.indexOf(':') >= 0 && IPV6.matcher(literal).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean octetsInRange(String literal) {
        for (String octet : literal.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static List<Cidr> parseCidrs(String raw) {
        List<Cidr> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            Cidr cidr = Cidr.parse(item);
            if (cidr == null) {
                throw new IllegalArgumentException("Некорректный адрес доверенного прокси в http.trusted-proxies: " + item);
            }
            result.add(cidr);
        }
        return result;
    }

    record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String item) {
            String address = item;
            int prefix = -1;
            int slash = item.indexOf('/');
            if (slash >= 0) {
                address = item.substring(0, slash);
                try {
                    prefix = Integer.parseInt(item.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            InetAddress parsed = parseLiteral(normalize(address));
            if (parsed == null) {
                return null;
            }
            int bits = parsed.getAddress().length * 8;
            if (prefix < 0) {
                prefix = bits;
            }
            if (prefix > bits) {
                return null;
            }
            return new Cidr(parsed.getAddress(), prefix);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixLength % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remaining);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cidr other && prefixLength == other.prefixLength && Arrays.equals(network, other.network);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(network) + prefixLength;
        }
    }
}
