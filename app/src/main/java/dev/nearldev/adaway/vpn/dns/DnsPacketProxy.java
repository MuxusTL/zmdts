/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package dev.nearldev.adaway.vpn.dns;

import android.content.Context;

import dev.nearldev.adaway.data.DomainStore;
import dev.nearldev.adaway.data.DnsLogStore;
import dev.nearldev.adaway.data.AppAttribution;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * Creates and parses packets, and sends packets to a remote socket or the device using VpnWorker.
 */
public class DnsPacketProxy {
    // Choose a value that is smaller than the time needed to unblock a host.
    private static final int NEGATIVE_CACHE_TTL_SECONDS = 5;
    private static final SOARecord NEGATIVE_CACHE_SOA_RECORD;

    static {
        try {
            // Let's use a guaranteed invalid hostname here, clients are not supposed to use
            // our fake values, the whole thing just exists for negative caching.
            Name name = new Name("adaway.vpn.invalid.");
            NEGATIVE_CACHE_SOA_RECORD = new SOARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS,
                    name, name, 0, 0, 0, 0, NEGATIVE_CACHE_TTL_SECONDS);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
    }

    private final EventLoop eventLoop;
    private final DnsServerMapper dnsServerMapper;
    private DomainStore domainStore;
    private DnsLogStore logStore;
    private Context context;

    public DnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        this.eventLoop = eventLoop;
        this.dnsServerMapper = dnsServerMapper;
    }

    /**
     * Initializes the rules database and the list of upstream servers.
     *
     * @param context The context we are operating in (for the database).
     */
    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.domainStore = DomainStore.getInstance(context);
        this.logStore = DnsLogStore.getInstance(context);
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    public void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        UdpPacket udpOutPacket = (UdpPacket) requestPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(requestPacket.getHeader().getDstAddr())
                .dstAddr(requestPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder().rawData(responsePayload)
                );

        IpPacket ipOutPacket;
        if (requestPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) requestPacket)
                    .srcAddr((Inet4Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) requestPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) requestPacket)
                    .srcAddr((Inet6Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) requestPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        this.eventLoop.queueDeviceWrite(ipOutPacket);
    }

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws IOException If some network error occurred
     */
    public void handleDnsRequest(byte[] packetData) throws IOException {
        IpPacket ipPacket;
        try {
            ipPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Timber.i(e, "handleDnsRequest: Discarding invalid IP packet");
            return;
        }

        // Check UDP protocol
        if (ipPacket.getHeader().getProtocol() != IpNumber.UDP) {
            return;
        }

        UdpPacket updPacket;
        Packet udpPayload;

        try {
            updPacket = (UdpPacket) ipPacket.getPayload();
            udpPayload = updPacket.getPayload();
        } catch (Exception e) {
            Timber.i(e, "handleDnsRequest: Discarding unknown packet type %s", ipPacket.getHeader());
            return;
        }

        InetAddress packetAddress = ipPacket.getHeader().getDstAddr();
        int packetPort = updPacket.getHeader().getDstPort().valueAsInt();
        Optional<InetAddress> dnsAddressOptional = this.dnsServerMapper.getDnsServerFromFakeAddress(packetAddress);
        if (!dnsAddressOptional.isPresent()) {
            Timber.w("Cannot find mapped DNS for %s.", packetAddress.getHostAddress());
            return;
        }
        InetAddress dnsAddress = dnsAddressOptional.get();

        if (udpPayload == null) {
            Timber.i("handleDnsRequest: Sending UDP packet without payload: %s", updPacket);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, dnsAddress, packetPort);
            eventLoop.forwardPacket(outPacket);
            return;
        }

        byte[] dnsRawData = udpPayload.getRawData();
        Message dnsMsg;
        try {
            dnsMsg = new Message(dnsRawData);
        } catch (IOException e) {
            Timber.i(e, "handleDnsRequest: Discarding non-DNS or invalid packet");
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Timber.i("handleDnsRequest: Discarding DNS packet with no query %s", dnsMsg);
            return;
        }
        Name name = dnsMsg.getQuestion().getName();
        String dnsQueryName = name.toString(true);
        boolean blocked = isBlocked(dnsQueryName);

        if (this.logStore != null && this.logStore.isEnabled()) {
            logQuery(dnsQueryName, blocked, ipPacket.getHeader().getSrcAddr(), updPacket.getHeader().getSrcPort().valueAsInt(), packetAddress, packetPort);
        }

        if (blocked) {
            Timber.i("handleDnsRequest: DNS Name %s blocked!", dnsQueryName);
            dnsMsg.getHeader().setFlag(Flags.QR);
            dnsMsg.getHeader().setRcode(Rcode.NOERROR);
            dnsMsg.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY);
            handleDnsResponse(ipPacket, dnsMsg.toWire());
        } else {
            Timber.i("handleDnsRequest: DNS Name %s allowed, sending to %s.", dnsQueryName, dnsAddress);
            DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, dnsAddress, packetPort);
            this.eventLoop.forwardPacket(outPacket, data -> handleDnsResponse(ipPacket, data));
        }
    }

    private boolean isBlocked(String dnsQueryName) {
        String hostname = dnsQueryName.toLowerCase(Locale.ENGLISH);
        return this.domainStore != null && this.domainStore.isBlocked(hostname);
    }

    private void logQuery(String domain, boolean blocked, InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort) {
        if (this.context == null) {
            return;
        }
        new Thread(() -> {
            AppAttribution.Result attribution = AppAttribution.resolve(this.context, localAddr, localPort, remoteAddr, remotePort);
            this.logStore.add(domain, blocked, attribution.label, attribution.packageName);
        }).start();
    }

    /**
     * Interface abstracting away VpnWorker.
     */
    public interface EventLoop {
        /**
         * Forward a packet to the VPN underlying network.
         *
         * @param packet The packet to forward.
         * @throws IOException If the packet could not be forwarded.
         */
        void forwardPacket(DatagramPacket packet) throws IOException;

        /**
         * Forward a packet to the VPN underlying network.
         *
         * @param packet   The packet to forward.
         * @param callback The callback to call with the packet response data.
         * @throws IOException If the packet could not be forwarded.
         */
        void forwardPacket(DatagramPacket packet, Consumer<byte[]> callback) throws IOException;

        /**
         * Write an IP packet to the local TUN device
         *
         * @param packet The packet to write (a response to a DNS request)
         */
        void queueDeviceWrite(IpPacket packet);
    }
}
