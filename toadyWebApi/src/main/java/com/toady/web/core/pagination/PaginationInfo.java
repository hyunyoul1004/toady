package com.toady.web.core.pagination;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pagination
 *
 * @author hyunyoul
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.NONE) //기본 생성자 사용못하도록 막음
public class PaginationInfo {

	/**
	 * 생성자
	 *
	 * @param pageNumber
	 * @param numberOfElements
	 * @param totalElements
	 * @param totalPageBlocks
	 * @param pageBlockSize
	 */
	public PaginationInfo(int pageNumber, int numberOfElements, long totalElements, int totalPageBlocks, int pageBlockSize, int recordPerCount) {

		this.pageNumber = pageNumber;
		this.numberOfElements = numberOfElements;
		this.totalElements = totalElements;
		this.totalPages = totalPageBlocks; //전체 페이지 블럭사이즈
		this.pageBlockSize = pageBlockSize;
		this.recordPerCount = recordPerCount; //한페이지에 보여지는 갯수 (프론트에서 numbering 하기위함.)

		init();
	}

	private int pageNumber; //현재 페이지
	private int numberOfElements; //현재 페이지에 나올 데이터 수
	private long totalElements;  //전체 데이터 수
	private int pageBlockSize; //페이지 블럭 수
	private int totalPages; //전체 페이지 수
	private int recordPerCount; //한페이지에 보여지는 갯수 (프론트에서 numbering 하기위함.)

	private int firstBlockPageNo; //첫번째 블럭 페이지 번호
	private int lastBlockPageNo; //마지막 블럭 페이지 번호

	private int firstPageNo = 1; //맨앞으로 갈 페이지 번호
	private int lastPageNo; //맨뒤로 갈 페이지 번호

	private int pageBlockNo; //페이지 블럭 번호

	private int preBlockPageNo; //이전 블럭 페이지번호
	private int nextBlockPageNo; //다음 블럭 페이지번호

	/**
	 * 초기화
	 * 0 기반 페이지를 화면용 1 기반 페이지 블록과 이전·다음 링크 번호로 변환합니다.
	 */
	private void init() {
		pageNumber = pageNumber + 1; //페이지넘버가 0부터 시작되므로 +1 해야함.

		this.pageBlockNo = pageNumber / pageBlockSize;
		if (pageNumber % pageBlockSize == 0) {
			this.pageBlockNo = pageBlockNo - 1;
		}

		if (pageNumber > pageBlockSize) {
			this.preBlockPageNo = pageNumber - (pageNumber % pageBlockSize);
			if (pageNumber % pageBlockSize == 0) {
				this.preBlockPageNo = pageNumber - pageBlockSize;
			}
		}

		this.firstBlockPageNo = pageBlockNo * pageBlockSize + 1;
		this.lastBlockPageNo = (pageBlockNo + 1) * pageBlockSize + 1;

		if (lastBlockPageNo > totalPages) {
			this.lastBlockPageNo = totalPages + 1;
		}

		this.nextBlockPageNo = (pageBlockNo + 1) * pageBlockSize;
		this.lastPageNo = totalPages;
	}

}
