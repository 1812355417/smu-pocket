exports.http = {
  RequestMethod: { GET: 'GET', POST: 'POST' },
  HttpDataType: { STRING: 0 },
  createHttp: () => ({ request: async () => { throw new Error('测试中禁止网络'); }, destroy() {} }),
};
