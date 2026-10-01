const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup() {
    const elements = new Map();
    const context = vm.createContext({console, window: {}, document: {
        getElementById(id) {
            if (!elements.has(id)) elements.set(id, {textContent: '', innerHTML: '', value: ''});
            return elements.get(id);
        }, querySelectorAll() { return []; }, addEventListener() {}
    }});
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8'), context);
    context.drawn = null;
    vm.runInContext('drawKLineChart = points => { drawn = points; }; showToast = () => {}; state.currentUser = {id:1};', context);
    return {context, elements};
}

test('empty market clears chart and displays unavailable instead of synthetic candles', () => {
    const {context, elements} = setup();
    context.data = {watchedSymbol: 'sh000001', kLines: [], indices: [], dataStatus: '行情不可用'};
    vm.runInContext('renderMarketToday(data)', context);
    assert.equal(context.drawn.length, 0);
    assert.match(elements.get('marketIndexList').innerHTML, /暂时不可用/);
    assert.match(elements.get('marketChartMeta').textContent, /暂时不可用/);
});

test('renders source and quote time separately from refresh time; missing amount stays null', () => {
    const {context, elements} = setup();
    context.data = {generatedAt: '2026-09-30T14:30:00', watchedName: '上证指数', kLineSource: '腾讯财经',
        indices: [{name: '上证指数', latest: '3845.37', change: '14.92', changePercent: '0.39',
            source: '腾讯财经', quotedAt: '2026-09-30T14:22:42'}],
        kLines: [{date: '2026-09-30', open: 3839, close: 3845, high: 3851, low: 3833, amount: null}]};
    vm.runInContext('renderMarketToday(data)', context);
    assert.match(elements.get('marketIndexList').innerHTML, /腾讯财经.*行情时间/);
    assert.match(elements.get('marketUpdatedAt').textContent, /页面刷新/);
    assert.match(elements.get('marketChartMeta').textContent, /最后交易日：2026-09-30/);
    assert.equal(context.drawn[0].amount, null);
});

test('late response for previous symbol cannot overwrite the latest market request', async () => {
    const {context, elements} = setup();
    let finishFirst;
    context.fakeRequest = url => url.includes('sh000001') ? new Promise(resolve => finishFirst = resolve)
        : Promise.resolve({data: {watchedSymbol: 'sz000001', watchedName: '平安银行', indices: [], kLines: []}});
    vm.runInContext('request = fakeRequest;', context);
    const old = vm.runInContext('loadMarketToday("sh000001")', context);
    await vm.runInContext('loadMarketToday("sz000001")', context);
    finishFirst({data: {watchedSymbol: 'sh000001', watchedName: '上证指数'}});
    await old;
    assert.equal(elements.get('marketChartTitle').textContent, '平安银行 K 线');
});

test('network failure clears the previous chart and context', async () => {
    const {context, elements} = setup();
    context.fakeRequest = async () => { throw Error('offline'); };
    vm.runInContext('request = fakeRequest; state.marketData = {watchedSymbol:"sh000001"};', context);
    await vm.runInContext('loadMarketToday("sz000001")', context);
    assert.equal(context.drawn.length, 0);
    assert.equal(vm.runInContext('state.marketData', context), null);
    assert.equal(elements.get('marketChartTitle').textContent, 'K 线');
});
