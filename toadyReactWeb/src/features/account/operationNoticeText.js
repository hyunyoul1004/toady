/** 운영 안내 문구. 회원에게는 종류·기간·사유만 보여 준다(관리자 메모는 서버가 보내지 않는다). */
function until(value) {
  if (!value) return null;
  const date = new Date(value.length === 16 ? `${value}:00` : value);
  if (Number.isNaN(date.getTime())) return null;
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

export function noticeText(item = {}) {
  const reason = item.reason || '운영 정책 위반';
  if (item.kind === 'STORY_BAN') {
    const end = until(item.endAt);
    return { title: '이야기 쓰기가 제한됐어요', body: end ? `${reason} 사유로 ${end}까지 이야기와 답글을 쓸 수 없어요. 공고 보기와 저장은 그대로 쓸 수 있어요.` : `${reason} 사유로 이야기와 답글을 쓸 수 없는 계정이에요. 문의는 dukkeoba@gmail.com으로 보내 주세요.` };
  }
  if (item.kind === 'NICKNAME_RESET') {
    return { title: '닉네임이 기본값으로 바뀌었어요', body: '닉네임이 운영 정책에 맞지 않아 회원 번호로 바꿨어요. 내 정보에서 새 닉네임을 정해 주세요.' };
  }
  return { title: '운영 정책 안내', body: `${reason} 관련 신고가 확인되어 안내드려요. 같은 일이 반복되면 이야기 쓰기나 쪽지 보내기가 제한될 수 있어요.` };
}
