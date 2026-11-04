

/**
 * 켈린더에 표시되는 일정 글씨 색상
 * @param {*} rceptSe 
 * @returns 
 */
 function fn_rceptSeColor(rceptSe){

  var color;
  if(rceptSe === "01" || rceptSe === "02" || rceptSe === "06"  || 
     rceptSe === "04" || rceptSe === "05"  || rceptSe === "08"){
    color = "#ffffff";
  } else {
    color = "#4B4B4B";
  }

  return color;
}

/**
* 켈린더에 표시되는 일정 및 검색 체크박스 색상
* @param {*} rceptSe 
* @returns 
*/
function fn_rceptSeBgColor(rceptSe){

  var bgColor;
  if(rceptSe == "01"){ //특별공급
    bgColor = "#9F496E";
  }else if(rceptSe == "02"){ //1순위
    bgColor = "#E0475B";
  }else if(rceptSe == "03"){ //2순순위
    bgColor = "#F8D4BA"; 

  }else if(rceptSe == "04"){ //공공지원민간임대
    bgColor = "#6F4849";
  }else if(rceptSe == "05"){ //오피스텔
    bgColor = "#8CBDB9";
  }else if(rceptSe == "06"){ //무순위
    bgColor = "#647295";
  }else if(rceptSe == "07"){ //임의공급
    bgColor = "#dcdcdc";
  }else if(rceptSe == "08"){ //불법행위재공급 (청약홈 청약캘린더 민트색)
    bgColor = "#2dd4bf";
  }else if(rceptSe == "09"){ //민간사전청약 1순위
    bgColor = "#F3D849";
  }else if(rceptSe == "10"){ //민간사전청약 2순위
    bgColor = "#FAE6B1";
  }else{
    bgColor = "#ff4040";
  }
  

  return bgColor;
}

export {fn_rceptSeColor, fn_rceptSeBgColor}

