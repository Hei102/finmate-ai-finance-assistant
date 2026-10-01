const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup() {
    const elements = new Map();
    const element = () => ({ textContent: '', value: '', disabled: false, style: {}, children: [],
        append(...children) { this.children.push(...children); },
        replaceChildren() { this.children = []; this.textContent = ''; },
        addEventListener() {}, querySelectorAll() { return []; } });
    const context = vm.createContext({ URL, console, window: {}, document: {
        getElementById(id) { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); },
        querySelectorAll() { return []; }, addEventListener() {}, createElement: element
    }});
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8'), context);
    vm.runInContext(`state.currentUser = {id: 1}; state.selectedFinancialReport = {symbol: '600519', reportId: 'r1'};
        reportRagQuestion.value = '营业收入？';`, context);
    return { context, elements };
}

test('first question indexes once; later questions reuse index and render source as text', async () => {
    const {context, elements} = setup();
    const calls = [];
    context.fakeRequest = async url => {
        calls.push(url);
        return url.endsWith('/index') ? {data: {documentId: 'd1'}} : {data: {
            answer: '<script>example</script>[1]', coverage: '部分片段',
            sources: [{id: 1, pageNumber: 7, snippet: '<img>原文', sourceUrl: 'https://static.cninfo.com.cn/a.pdf#page=7'}]
        }};
    };
    vm.runInContext('request = fakeRequest;', context);
    await vm.runInContext('handleReportRagQuestion({preventDefault(){}})', context);
    await vm.runInContext('handleReportRagQuestion({preventDefault(){}})', context);
    assert.equal(calls.filter(url => url.endsWith('/index')).length, 1);
    const output = elements.get('reportRagOutput');
    assert.equal(output.children[0].textContent, '<script>example</script>[1]');
    assert.equal(output.children[2].children[1].textContent, '<img>原文');
    assert.equal(output.children[2].children[2].rel, 'noopener noreferrer');
    assert.equal(elements.get('reportRagSubmit').disabled, false);
});

test('logout discards pending index response and clears document cache', async () => {
    const {context, elements} = setup();
    let finish;
    context.fakeRequest = () => new Promise(resolve => { finish = resolve; });
    vm.runInContext('request = fakeRequest;', context);
    const pending = vm.runInContext('handleReportRagQuestion({preventDefault(){}})', context);
    vm.runInContext('state.currentUser = null; resetFinancialReportState();', context);
    finish({data: {documentId: 'old-account-document'}});
    await pending;
    assert.equal(vm.runInContext('state.ragDocuments.size', context), 0);
    assert.equal(elements.get('reportRagOutput').textContent, '');
    assert.equal(elements.get('reportRagSubmit').disabled, true);
});
