// In-memory persistence for the Inama v1 API (demo mode / tests).
//
// Every method is async so a MongoDB implementation (reusing AgriAI's mongoose setup) can be
// dropped in with the same interface: createStore() is the only place that chooses one.
// Photos are never stored — only the diagnosis result and image metadata.

export class MemoryStore {
  constructor() {
    this.farmers = new Map(); // id → farmer
    this.farmersByPhone = new Map(); // phone → id
    this.otps = new Map(); // phone → { code, expiresAt, attempts }
    this.diagnoses = new Map(); // id → diagnosis (with farmerId)
    this.answers = new Map(); // id → answer (with farmerId)
    this.cases = new Map(); // id → expert case
  }

  async saveOtp(phone, record) {
    this.otps.set(phone, record);
  }
  async getOtp(phone) {
    return this.otps.get(phone) || null;
  }
  async deleteOtp(phone) {
    this.otps.delete(phone);
  }

  async findFarmerByPhone(phone) {
    const id = this.farmersByPhone.get(phone);
    return id ? this.farmers.get(id) : null;
  }
  async getFarmer(id) {
    return this.farmers.get(id) || null;
  }
  async saveFarmer(farmer) {
    this.farmers.set(farmer.id, farmer);
    this.farmersByPhone.set(farmer.phone, farmer.id);
    return farmer;
  }

  async saveDiagnosis(farmerId, diagnosis) {
    this.diagnoses.set(diagnosis.id, { ...diagnosis, farmerId });
    return diagnosis;
  }
  async getDiagnosis(farmerId, id) {
    const d = this.diagnoses.get(id);
    return d && d.farmerId === farmerId ? d : null;
  }
  async listDiagnoses(farmerId) {
    return [...this.diagnoses.values()].filter((d) => d.farmerId === farmerId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
  async updateDiagnosis(farmerId, id, patch) {
    const d = await this.getDiagnosis(farmerId, id);
    if (!d) return null;
    const next = { ...d, ...patch };
    this.diagnoses.set(id, next);
    return next;
  }

  async saveAnswer(farmerId, answer) {
    this.answers.set(answer.id, { ...answer, farmerId });
    return answer;
  }
  async listAnswers(farmerId) {
    return [...this.answers.values()].filter((a) => a.farmerId === farmerId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  async saveCase(expertCase) {
    this.cases.set(expertCase.id, expertCase);
    return expertCase;
  }
  async getCase(farmerId, id) {
    const c = this.cases.get(id);
    return c && c.farmerId === farmerId ? c : null;
  }
}

let store = null;
export function getStore() {
  if (!store) store = new MemoryStore();
  return store;
}
export function resetStore() {
  store = new MemoryStore();
  return store;
}

export const strip = (record) => {
  if (!record) return record;
  const { farmerId, ...rest } = record;
  return rest;
};
