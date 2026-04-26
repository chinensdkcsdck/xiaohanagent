package com.atguigu.java.ai.langchain4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mq")
public class MqProperties {

    private boolean enabled = false;
    private String bookingExchange = "booking.exchange";
    private String bookingQueue = "booking.notify.queue";
    private String bookingRoutingKey = "booking.created";
    private String bookingDlxExchange = "booking.dlx.exchange";
    private String bookingDlxQueue = "booking.dlx.queue";
    private String bookingDlxRoutingKey = "booking.dlx";
    private int listenerMaxAttempts = 3;
    private long listenerInitialIntervalMs = 500;
    private double listenerMultiplier = 2.0d;
    private long listenerMaxIntervalMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBookingExchange() {
        return bookingExchange;
    }

    public void setBookingExchange(String bookingExchange) {
        this.bookingExchange = bookingExchange;
    }

    public String getBookingQueue() {
        return bookingQueue;
    }

    public void setBookingQueue(String bookingQueue) {
        this.bookingQueue = bookingQueue;
    }

    public String getBookingRoutingKey() {
        return bookingRoutingKey;
    }

    public void setBookingRoutingKey(String bookingRoutingKey) {
        this.bookingRoutingKey = bookingRoutingKey;
    }

    public String getBookingDlxExchange() {
        return bookingDlxExchange;
    }

    public void setBookingDlxExchange(String bookingDlxExchange) {
        this.bookingDlxExchange = bookingDlxExchange;
    }

    public String getBookingDlxQueue() {
        return bookingDlxQueue;
    }

    public void setBookingDlxQueue(String bookingDlxQueue) {
        this.bookingDlxQueue = bookingDlxQueue;
    }

    public String getBookingDlxRoutingKey() {
        return bookingDlxRoutingKey;
    }

    public void setBookingDlxRoutingKey(String bookingDlxRoutingKey) {
        this.bookingDlxRoutingKey = bookingDlxRoutingKey;
    }

    public int getListenerMaxAttempts() {
        return listenerMaxAttempts;
    }

    public void setListenerMaxAttempts(int listenerMaxAttempts) {
        this.listenerMaxAttempts = listenerMaxAttempts;
    }

    public long getListenerInitialIntervalMs() {
        return listenerInitialIntervalMs;
    }

    public void setListenerInitialIntervalMs(long listenerInitialIntervalMs) {
        this.listenerInitialIntervalMs = listenerInitialIntervalMs;
    }

    public double getListenerMultiplier() {
        return listenerMultiplier;
    }

    public void setListenerMultiplier(double listenerMultiplier) {
        this.listenerMultiplier = listenerMultiplier;
    }

    public long getListenerMaxIntervalMs() {
        return listenerMaxIntervalMs;
    }

    public void setListenerMaxIntervalMs(long listenerMaxIntervalMs) {
        this.listenerMaxIntervalMs = listenerMaxIntervalMs;
    }
}
