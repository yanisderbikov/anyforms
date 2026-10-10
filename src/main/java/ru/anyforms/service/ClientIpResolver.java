package ru.anyforms.service;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolver {

    String resolve(HttpServletRequest request);
}
