import { STAGE_LABEL, STAGE_PROMPTS } from './storyRules';
import './story.css';

/** 질문 칩을 누르면 말머리를 붙여 글쓰기를 시작한다. 선택된 칩은 다시 누르면 해제된다. */
export default function StoryPrompts({ stage, activeTag, onPick }) {
  const prompts = STAGE_PROMPTS[stage] || STAGE_PROMPTS.before;
  const active = prompts.find(prompt => prompt.tag === activeTag);
  return <div className="story-prompts">
    <div className="story-prompts-head"><span className="story-stage">{STAGE_LABEL[stage]}</span><strong>이런 이야기를 많이 나눠요</strong></div>
    <div className="story-prompt-chips" role="group" aria-label="이야기 주제 고르기">
      {prompts.map(prompt => <button type="button" key={prompt.tag} className={prompt.tag === activeTag ? 'is-active' : ''} aria-pressed={prompt.tag === activeTag}
        onClick={() => onPick(prompt.tag === activeTag ? null : prompt)}>#{prompt.tag}</button>)}
    </div>
    {active && <p className="story-prompt-question">{active.text}</p>}
  </div>;
}
