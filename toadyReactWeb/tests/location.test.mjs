import test from 'node:test';
import assert from 'node:assert/strict';
import {areaQuery,parcelCandidates,resolveLocation,siteCandidate,sameRegion} from '../src/features/announcement/location/resolveLocation.js';
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
