/* 페이지 양식: 줄/모눈/점은 CSS 배경, 플래너·레시피 저널은 마크업 */
(function (Y) {
'use strict';
const lb = (en, ko) => '<span class="lb">' + en + '<i>' + ko + '</i></span>';
const head = (title, link) =>
  '<div class="tl-head"><a class="tlink back" data-link="back" aria-label="뒤로가기">&#8249;</a><h3>' + title + '</h3>' +
  '<span class="tlink toc" data-link="toc">' + link + '</span></div>';

function recipeBlock() {
  const meta = [['DIFFICULTY', '난이도'], ['RATING (1-5)', '평점'], ['PREP TIME', '준비 시간'], ['COOK TIME', '조리 시간'], ['SERVINGS', '인분'], ['TOTAL TIME', '총 시간']]
    .map(m => '<div class="cell">' + lb(m[0], m[1]) + '<b></b></div>').join('');
  return '<div class="rb">' +
    '<div class="rb-top"><div class="rb-fields">' +
      '<div class="fld">' + lb('RECIPE NAME', '레시피 이름') + '<b></b></div>' +
      '<div class="fld">' + lb('LINK TO SOURCE', '출처 링크') + '<b></b></div></div>' +
      '<div class="rb-meta">' + meta + '</div></div>' +
    '<div class="rb-body"><div class="ing">' + lb('INGREDIENTS', '재료') + '<div class="box"></div></div>' +
      '<div class="dir">' + lb('DIRECTIONS', '조리 순서') + '<div class="lines"></div></div></div></div>';
}

function plannerDays() {
  return [['MON', '월'], ['TUE', '화'], ['WED', '수'], ['THU', '목'], ['FRI', '금'], ['SAT', '토'], ['SUN', '일']]
    .map(d => '<div class="day"><div class="dn">' + lb(d[0], d[1]) + '</div><div class="dl"></div></div>').join('');
}

Y.tplHTML = function (id) {
  if (id === 'recipe') return '<div class="tpl tpl-recipe">' + head('Recipe journal', '건강 &amp; 식단 목차') + recipeBlock() + recipeBlock() + '</div>';
  if (id === 'planner') return '<div class="tpl tpl-planner">' + head('Weekly planner', '월간 목차') +
    '<div class="pl-sub"><div class="fld">' + lb('WEEK OF', '주 시작일') + '<b></b></div><div class="fld">' + lb('FOCUS', '이번 주 목표') + '<b></b></div></div>' +
    '<div class="pl-days">' + plannerDays() + '</div>' +
    '<div class="pl-notes">' + lb('NOTES', '메모') + '<div class="box"></div></div></div>';
  return '';
};

Y.linkAction = function (name) {
  if (name === 'back') Y.closeEditor();
  else ;
};
})(window.Y);
