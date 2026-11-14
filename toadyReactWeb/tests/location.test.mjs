import test from 'node:test';
import assert from 'node:assert/strict';
import {areaQuery,parcelCandidates,resolveLocation,searchArea,siteCandidate,sameRegion} from '../src/features/announcement/location/resolveLocation.js';
function maps(addressRows=[],placeRows=[]) {
 const calls=[];
 return {calls,services:{Status:{OK:'OK'},Geocoder:class{addressSearch(q,cb){calls.push(['address',q]);cb(addressRows,'OK');}},Places:class{keywordSearch(q,cb){calls.push(['place',q]);cb(placeRows,'OK');}}}};
}
const row={address_type:'REGION_ADDR',x:'126.7',y:'37.7',address_name:'경기도 파주시 동패동 400-7',address:{address_name:'경기도 파주시 동패동 400-7',b_code:'4148012300',region_3depth_name:'동패동'}};
test('block-only addresses never resolve to city, station or model-house markers',async()=>{
 for(const address of ['경기도 구리시 구리갈매역세권 B-1블록','경기도 파주시 파주운정3지구 주상복합 4블록','인천광역시 서구 검단신도시 AA-1BL']) {
  const sdk=maps([row],[{place_name:'견본주택',address_name:address,x:'127',y:'37'}]);
  const result=await resolveLocation(sdk,{hssplyAdres:address,houseNm:'테스트 단지'});
  assert.equal(result.status,'UNRESOLVED');
  // 지번 검색은 하지 않고, 대략 지역(시·군·구 또는 법정동)만 한 번 묻는다. 결과는 확정 위치(latitude)가 아니라 area로만 준다.
  const queries=sdk.calls.filter(x=>x[0]==='address').map(x=>x[1]);
  assert.ok(queries.length<=1 && queries.every(q=>!/\d/.test(q)),queries.join());
  assert.equal(result.latitude,undefined);
 }
});
test('new-town names are not municipalities, so a bracketed parcel still resolves',()=>{
 const address='인천광역시 서구 검단신도시 AA21블록 (불로동 123)';
 assert.ok(parcelCandidates(address).includes('인천광역시 서구 불로동 123'));
 assert.equal(sameRegion(address,'인천 서구 불로동 123'),true);
 assert.deepEqual(parcelCandidates('경기도 파주시 운정신도시 A1블록'),[],'지번이 없으면 확정하지 않는다');
});
test('precise parcel, parentheses and municipality are validated together',async()=>{
 const detail={hssplyAdres:'경기도 파주시 운정3지구 (동패동 400-7번지 일원)',houseNm:'운정 단지'};
 assert.ok(parcelCandidates(detail.hssplyAdres).includes('경기도 파주시 동패동 400-7'));
 const result=await resolveLocation(maps([row]),detail);assert.equal(result.status,'ADDRESS');assert.equal(result.bcode,'4148012300');
 const wrongParcel={...row,address_name:'경기도 파주시 동패동 400',address:{...row.address,address_name:'경기도 파주시 동패동 400'}};
 assert.equal((await resolveLocation(maps([wrongParcel]),detail)).status,'UNRESOLVED');
 const wrongRegion={...row,address_name:'경기도 구리시 갈매동 400-7',address:{...row.address,address_name:'경기도 구리시 갈매동 400-7'}};
 assert.equal((await resolveLocation(maps([wrongRegion]),detail)).status,'UNRESOLVED');
});
test('name matches are unverified candidates and sales offices or wrong blocks are excluded',async()=>{
 const address='경기도 구리시 구리갈매역세권 B-1블록',house='구리갈매역세권 B-1블록 신안인스빌 디센트';
 const place={place_name:house,address_name:'경기도 구리시 갈매동 100',x:'127.1',y:'37.6'};
 assert.equal((await resolveLocation(maps([],[place]),{hssplyAdres:address,houseNm:house})).status,'CANDIDATE');
 assert.equal(siteCandidate({...place,place_name:house+' 견본주택'},address,house),false);
 assert.equal(siteCandidate({...place,place_name:house.replace('B-1','B-2')},address,house),false);
});
test('province aliases match while identically named counties in different provinces are rejected',()=>{
 assert.equal(sameRegion('경기도 구리시 갈매동 100','경기 구리시 갈매동 100'),true);
 assert.equal(sameRegion('강원특별자치도 고성군 간성읍 100','경남 고성군 고성읍 100'),false);
 assert.equal(sameRegion('부산광역시 중구 중앙대로 100','부산 중구 중앙대로 100'),true);
 assert.equal(sameRegion('세종특별자치시 한누리대로 100','세종특별자치시 한누리대로 100'),true);
});

test('지구·블록 주소는 법정동(여러 동이면 시·군·구) 일대만 추정으로 준다',async()=>{
 assert.deepEqual(areaQuery('경기도 안산시 단원구 신길동 일원 안산신길2 공공주택지구 내 B-1블록'),{query:'경기도 안산시 단원구 신길동',level:'dong',name:'신길동'});
 assert.deepEqual(areaQuery('경기도 구리시 구리갈매역세권 B-1블록'),{query:'경기도 구리시',level:'district',name:'구리시'});
 assert.deepEqual(areaQuery('인천광역시 계양구 귤현동, 동양동, 박촌동 일원 인천계양 공공주택지구'),{query:'인천광역시 계양구',level:'district',name:'계양구'},'여러 동에 걸치면 한 동을 고르지 않는다');
 assert.equal(areaQuery('경기'),null);
 const region={address_type:'REGION',x:'126.8',y:'37.3',address_name:'경기 안산시 단원구 신길동',address:{address_name:'경기 안산시 단원구 신길동',b_code:'4127310500',region_1depth_name:'경기',region_2depth_name:'안산시 단원구',region_3depth_name:'신길동'}};
 const result=await resolveLocation(maps([region]),{hssplyAdres:'경기도 안산시 단원구 신길동 일원 안산신길2 공공주택지구 내 B-1블록',houseNm:''});
 assert.equal(result.status,'UNRESOLVED');
 assert.deepEqual({level:result.area.level,name:result.area.name,bcode:result.area.bcode,dong:result.area.dong},{level:'dong',name:'신길동',bcode:'4127310500',dong:'신길동'});
 const other={...region,address_name:'경기 시흥시 신길동',address:{...region.address,address_name:'경기 시흥시 신길동'}};
 assert.equal((await resolveLocation(maps([other]),{hssplyAdres:'경기도 안산시 단원구 신길동 일원 B-1블록',houseNm:''})).area,null,'다른 시·군·구의 같은 동 이름은 쓰지 않는다');
});
test('map search moves to a dong, road or place without confirming a location',async()=>{
 const dong={address_type:'REGION',x:'126.75',y:'37.71',address_name:'경기 파주시 운정1동',address:{region_1depth_name:'경기',region_2depth_name:'파주시',region_3depth_name:'운정1동'}};
 const sdk=maps([dong]);
 const found=await searchArea(sdk,'운정동','인천 계양구');
 assert.deepEqual(found,{name:'경기 파주시 운정1동',latitude:37.71,longitude:126.75,level:5});
 // 공고 지역을 붙인 검색어를 먼저 묻는다(같은 이름의 동이 여러 지역에 있을 때 공고 지역이 앞서게).
 assert.equal(sdk.calls[0][1],'인천 계양구 운정동');
 const district={...dong,address_name:'인천 부평구',address:{region_1depth_name:'인천',region_2depth_name:'부평구',region_3depth_name:''}};
 assert.equal((await searchArea(maps([district]),'부평구')).level,6);
 const road={address_type:'ROAD',x:'127.05',y:'37.29',address_name:'경기 수원시 영통구 광교중앙로'};
 assert.equal((await searchArea(maps([road]),'광교중앙로')).level,4);
 // 주소로 못 찾으면 장소 이름으로, 그것도 없으면 null(지도를 옮기지 않는다).
 const place=await searchArea(maps([],[{place_name:'운정역 경의중앙선',address_name:'경기 파주시 와동동',x:'126.74',y:'37.72'}]),'운정역');
 assert.equal(place.name,'운정역 경의중앙선');
 assert.equal(await searchArea(maps([],[]),'없는곳'),null);
 assert.equal(await searchArea(maps([],[]),'  '),null);
});
test('address suggestions mark exact addresses and drop duplicate place names',async()=>{
 const {suggestAddresses}=await import('../src/features/announcement/location/resolveLocation.js');
 const exact={address_type:'ROAD_ADDR',x:'127.03',y:'37.5',address_name:'서울 강남구 테헤란로 152',address:{address_name:'서울 강남구 역삼동 737'}};
 const area={address_type:'REGION',x:'126.65',y:'37.38',address_name:'인천 연수구 송도동'};
 const places=[{id:'1',place_name:'인천 연수구 송도동',address_name:'x',x:'126.6',y:'37.3'},{id:'2',place_name:'송도한옥마을',road_address_name:'인천 연수구 테크노파크로 180',x:'126.64',y:'37.39'}];
 const items=await suggestAddresses(maps([exact,area],places),'송도');
 assert.deepEqual(items.map(item=>[item.kind,item.label,item.exact]),[['address','서울 강남구 테헤란로 152',true],['address','인천 연수구 송도동',false],['place','송도한옥마을',false]]);
 assert.equal(items[0].detail,'서울 강남구 역삼동 737');
 assert.deepEqual(await suggestAddresses(maps([exact]),'송'),[],'한 글자는 묻지 않는다');
});
