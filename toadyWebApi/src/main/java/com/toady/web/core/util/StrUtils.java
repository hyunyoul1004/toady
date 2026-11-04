package com.toady.web.core.util;

/**
 * packageName    : com.toady.web.core.util
 * fileName       : StrUtils
 * author         : youn
 * date           : 2022/06/11
 * description    : 문자열 유틸
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
public class StrUtils {

    //Code 생성
    public static String createCode() {
        String pswd = "";
        StringBuffer sb = new StringBuffer();
        //StringBuffer sc = new StringBuffer("!@#$%^&*-=?~"); // 특수문자 모음, {}[] 같은
        // 비호감문자는 뺌

        // 대문자 9개를 임의 발생
        sb.append((char) ((Math.random() * 26) + 65)); // 첫글자는 대문자, 첫글자부터 특수문자
        // 나오면 안 이쁨
        for (int i = 0; i < 10; i++) {
            sb.append((char) ((Math.random() * 26) + 65)); // 아스키번호 65(A) 부터
            // 26글자 중에서 택일
        }

        // 소문자 6개를 임의발생
		/*
		for (int i = 0; i < 7; i++) {
			sb.append((char) ((Math.random() * 26) + 97)); // 아스키번호 97(a) 부터
															// 26글자 중에서 택일
		}
		*/

        // 숫자 8개를 임의 발생
        for (int i = 0; i < 9; i++) {
            sb.append((char) ((Math.random() * 10) + 48)); // 아스키번호 48(1) 부터
            // 10글자 중에서 택일
        }

        int indexAry[] = new int[13];

        for (int i = 0; i < indexAry.length; i++) {
            indexAry[i] = (int) (Math.random() * 13) + 1;
            // 랜덤 값 반환
            for (int j = 0; j < i; j++) {
                if (indexAry[i] == indexAry[j]) {
                    i--;
                    break;
                } // 중복 값 제거1
            }
        }


        for (int i = 0; i < indexAry.length; i++) {
            if(i == 3 || i == 9){
                pswd += "-";
            }else{
                pswd += sb.toString().charAt(indexAry[i] - 1);
            }
        }
        return pswd;
    }

}
