package com.toady.web.apt.service;

/** DB 잠금을 다른 수집이 보유한 상태. 관리자는 일반 수집 실패와 구분해 대기 후 재시도한다. */
public class ApplyhomeSyncBusyException extends IllegalStateException {
    public ApplyhomeSyncBusyException() {
        super("Another Applyhome synchronization is already running");
    }
}
