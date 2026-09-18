package com.inshore.shared.security;

public class JwtConstants {
    // The JWT secret now comes from the "jwt.secret" property (jwt.secret=${INSHORE_JWT_SECRET})
    // instead of being hardcoded here. See JwtProvider / SecurityConfig.
    public static final String JWT_HEADER = "Authorization";
}
