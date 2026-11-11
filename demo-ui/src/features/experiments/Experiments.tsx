import { ArrowIcon } from '../../shared/ui/Select';

export interface DemoInfo {
  gateway: string; operatorServices: string[]; timeTravel: boolean; links: { title: string; url: string }[];
}
export function Experiments({ info }: { info?: DemoInfo }) {
  return <div className="experiment-content">
    <div className="section-heading"><span>실험 제어</span><span className="badge neutral">로컬 전용</span></div>
    <section><h3>모니터링</h3><div className="monitor-links">{info?.links.map(link =>
      <a key={link.title} href={link.url} target="_blank" rel="noreferrer">{link.title}<ArrowIcon /></a>)}</div>
      <p className="caption">서버에 설정된 주소로 연결합니다. 모니터링 도구를 먼저 켜고 실제 요청 시각의 지표를 확인하세요.</p></section>
    <section><h3>업무 시간</h3><p>배송 예정·환불 마감·정산 준비 시각은 실제 응답에서 확인합니다.</p>
      <button disabled title="여러 서비스와 DB 실행 시각을 분리하는 백엔드 기능이 필요합니다.">다음 업무 시각으로 이동</button>
      <p className="caption">아직 지원하지 않습니다. 브라우저 시각 변경이나 DB 수정으로 대신하지 않습니다.</p></section>
  </div>;
}
