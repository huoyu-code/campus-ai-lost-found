package com.campus.lostfound.application.enums;

/**
 * 申请状态机。
 *
 * <pre>
 * PENDING -> ACCEPTED
 *    |  \
 *    |   +-> REJECTED
 *    +-----> WITHDRAWN
 * </pre>
 */
public enum ApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
