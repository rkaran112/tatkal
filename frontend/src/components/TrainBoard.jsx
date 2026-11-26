// The yellow station signboard at the top: which train, which coach, and how full it is.
export default function TrainBoard({ free, held, booked }) {
  return (
    <section className="board" aria-label="Train details">
      <div className="board__sign">
        <p className="board__number">12951</p>
        <h1 className="board__name">Tatkal Express</h1>
        <dl className="board__trip">
          <div>
            <dt>Journey</dt>
            <dd>Friday, 25 December 2026</dd>
          </div>
          <div>
            <dt>Coach</dt>
            <dd>S1, sleeper class</dd>
          </div>
        </dl>
      </div>

      <dl className="board__counts">
        <div className="count count--free">
          <dt>Free</dt>
          <dd>{free}</dd>
        </div>
        <div className="count count--held">
          <dt>On hold</dt>
          <dd>{held}</dd>
        </div>
        <div className="count count--booked">
          <dt>Booked</dt>
          <dd>{booked}</dd>
        </div>
      </dl>
    </section>
  );
}
