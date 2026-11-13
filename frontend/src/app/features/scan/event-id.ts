/**
 * Identifiant d'événement d'un scan : gardé tant que le scan n'est pas accepté et que la saisie
 * ne change pas (un renvoi après coupure ne crée pas de doublon), neuf sinon.
 */
export class EventIdKeeper {
  private id: string | null = null;
  private signature: string | null = null;

  idFor(input: unknown): string {
    const signature = JSON.stringify(input);
    if (this.id === null || signature !== this.signature) {
      this.id = crypto.randomUUID();
      this.signature = signature;
    }
    return this.id;
  }

  accepted(): void {
    this.id = null;
    this.signature = null;
  }
}
