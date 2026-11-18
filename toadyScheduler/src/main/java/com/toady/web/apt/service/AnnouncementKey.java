package com.toady.web.apt.service;

/**
 * 공고 하나를 가리키는 키(주택관리번호 + 공고번호). 청약홈 데이터의 기본키 조합과 같다.
 * 수집 서비스가 공고 목록을 모으고 중복을 거를 때 쓴다.
 */
public record AnnouncementKey(String houseManageNo, String pblancNo) {
}
