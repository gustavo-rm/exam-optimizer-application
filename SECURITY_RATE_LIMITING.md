# Rate Limiting & Proxy Security Architecture

The Dynamic Study Planner API is highly computationally intensive due to the usage of Genetic Algorithms. Therefore, protecting the `/api/v1/optimizer` endpoints against Denial-of-Service (DoS) and excessive compute utilization is a critical security requirement.

This document outlines the architectural decisions and configurations implemented to secure the rate-limiting functionality.

## 1. Rate Limiting Strategy (Bucket4j + Caffeine)

We utilize **Bucket4j** to implement the Token Bucket algorithm. State is stored locally in memory using a **Caffeine** cache.
- **Configuration:** Limits (capacity and refill rates) are dynamically configurable via `application.properties`.
- **Eviction:** To prevent memory exhaustion attacks, the Caffeine cache is bounded to 10,000 active IPs and evicts entries after 60 minutes of inactivity.
- **Fail-Fast Delegation:** Instead of manually formatting error responses inside the filter, the `RateLimitingFilter` passes the exception to Spring's `HandlerExceptionResolver`. This ensures that standard RFC 7807 Problem Details are consistently emitted by the `@RestControllerAdvice`.

*Note for production:* For horizontal scaling across multiple pods (e.g., Kubernetes), this implementation must be upgraded to a distributed caching backend like Redis (using Bucket4j's JCache support) to ensure limits are enforced globally rather than per-pod.

## 2. Preventing IP Spoofing & Header Forgery

A common flaw in rate-limiting filters is manually extracting the `X-Forwarded-For` header to determine the client IP. Malicious actors can spoof this header (e.g., `X-Forwarded-For: 1.2.3.4`) to bypass IP-based bucket limits or intentionally rate-limit the upstream proxy itself.

### The Mitigation: Tiered Keying & Spring Boot Native Proxy Handling

Our `RateLimitingFilter` utilizes a **tiered keying strategy** and relies exclusively on Tomcat's trusted internal proxy mechanisms rather than manual header parsing.

1. **Tier 1 - Authenticated Principal:** If Spring Security provides an authenticated `UserPrincipal`, it is used as the primary Bucket4j key.
2. **Tier 2 - API Key:** A custom `X-API-Key` header provides the next tier of uniqueness.
3. **Tier 3 - Secure IP Fallback:** If the request is anonymous, the bucket key falls back to `request.getRemoteAddr()`.

To ensure `request.getRemoteAddr()` returns the *true* client IP behind load balancers/ingresses, we configure Tomcat's `RemoteIpValve` via `application.properties`:

```properties
# Relies on Tomcat's internal RemoteIpValve
server.forward-headers-strategy=native

# Strict validation of upstream proxies
server.tomcat.remoteip.internal-proxies=10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1
```

### Why this is secure:
Tomcat will *only* parse the `X-Forwarded-For` header if the incoming TCP connection originates from an IP matching the `internal-proxies` regex (representing your trusted Load Balancer, AWS ALB, or Kubernetes NGINX Ingress).
If an attacker sends a spoofed `X-Forwarded-For` directly to the application server (bypassing the proxy), Tomcat rejects the header and correctly uses the attacker's actual IP address (`remoteAddr`), rendering the spoofing attempt useless.

**Action Required for Deployment:**
The default regex trusts standard RFC 1918 private subnets. For maximum production security, you **must** narrow down `server.tomcat.remoteip.internal-proxies` to the specific CIDR block of your Load Balancer or Ingress Controller.
